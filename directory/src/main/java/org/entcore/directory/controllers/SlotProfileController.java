package org.entcore.directory.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.directory.pojo.Slot;
import org.entcore.directory.services.SlotProfileService;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.*;


public class SlotProfileController extends MongoDbControllerHelper {

    private SlotProfileService slotProfileService;


    public SlotProfileController(String collection) {
        super(collection);
    }

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    @Post("/slotprofiles")
    @ApiDoc("Create a slot profile")
    @SecuredAction(value = "directory.slot.manage")
    public void createSlotProfile(final HttpServerRequest request) {

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createSlotProfile", new Handler<JsonObject>() {
                        @Override
                        public void handle(final JsonObject slotProfile) {
                            final String structureId = slotProfile.getString("schoolId");
                            final boolean isCreation = true;
                            slotProfileService.listSlotProfilesByStructure(structureId, getCreateOrUpdateSlotProfileHandler(slotProfile, request, isCreation));
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }

    @Put("/slotprofiles/:idSlotProfile")
    @ApiDoc("Update a slot profile")
    @SecuredAction(value = "directory.slot.manage")
    public void updateSlotProfile(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createSlotProfile", new Handler<JsonObject>() {
                        @Override
                        public void handle(final JsonObject slotProfile) {
                            final String structureId = slotProfile.getString("schoolId");
                            final String idSlotProfile = request.params().get("idSlotProfile");
                            slotProfile.put("_id", idSlotProfile);
                            final boolean isCreation = false;
                            slotProfileService.listSlotProfilesByStructure(structureId, getCreateOrUpdateSlotProfileHandler(slotProfile, request, isCreation));
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }

    @Delete("/slotprofiles/:idSlotProfile")
    @ApiDoc("Delete a slot profile")
    @SecuredAction(value = "directory.slot.manage")
    public void deleteSlotProfile(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String idSlotProfile = request.params().get("idSlotProfile");
                    slotProfileService.deleteSlotProfile(idSlotProfile, notEmptyResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    private Handler<Either<String, JsonArray>> getCreateOrUpdateSlotProfileHandler(final JsonObject slotProfile, final HttpServerRequest request, final boolean isCreation) {
        return new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> event) {
                if (event.isRight()) {
                    JsonArray value = event.right().getValue();
                    if (!newSlotProfileNameCanByUsed(value, slotProfile)) {
                        String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                "directory.slot.bad.request.profile.already.exists",
                                Renders.getHost(request),
                                I18n.acceptLanguage(request));
                        badRequest(request, errorMessage);
                    } else {
                        if (isCreation) {
                            create(request);
                        } else {
                            String id = slotProfile.getString("_id");
                            String name = slotProfile.getString("name");
                            slotProfileService.updateSlotProfile(id, name, notEmptyResponseHandler(request));
                        }
                    }
                } else {
                    log.error("Error when calling service listSlotProfilesByStructure: " + event.left().getValue());
                    badRequest(request);
                }
            }
        };
    }


    @Post("/slotprofiles/:idSlotProfile/slots")
    @ApiDoc("Create a slot for a given slot profile")
    @SecuredAction(value = "directory.slot.manage")
    public void createSlot(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createSlot", new Handler<JsonObject>() {
                        @Override
                        public void handle(final JsonObject jsonSlot) {
                            final String idSlotProfile = request.params().get("idSlotProfile");
                            final Slot slot = new Slot(jsonSlot);
                            Long slotStart = slot.getStart();
                            Long slotEnd = slot.getEnd();
                            if (slotStart == null || slotEnd == null) {
                                String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                        "directory.slot.bad.request.format.hours.invalid",
                                        Renders.getHost(request),
                                        I18n.acceptLanguage(request));
                                badRequest(request, errorMessage);
                                return;
                            }
                            if (slotStart >= slotEnd) {
                                String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                        "directory.slot.bad.request.invalid.hours",
                                        Renders.getHost(request),
                                        I18n.acceptLanguage(request));
                                badRequest(request, errorMessage);
                                return;
                            }

                            // check name unicity for given profile
                            slotProfileService.listSlots(idSlotProfile, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight()) {
                                        JsonObject value = event.right().getValue();
                                        JsonArray existingSlots = value.getJsonArray("slots");
                                        if (slotNameAlreadyExists(existingSlots, slot)) {
                                            String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                                    "directory.slot.bad.request.slot.name.already.exists",
                                                    Renders.getHost(request),
                                                    I18n.acceptLanguage(request));
                                            badRequest(request, errorMessage);
                                            return;
                                        }
                                        // check no overlapping
                                        if (overlapAnotherSlot(existingSlots, slot)) {
                                            String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                                    "directory.slot.bad.request.slot.overlap",
                                                    Renders.getHost(request),
                                                    I18n.acceptLanguage(request));
                                            badRequest(request, errorMessage);
                                        } else {
                                            slotProfileService.createSlot(idSlotProfile, jsonSlot, notEmptyResponseHandler(request));
                                        }
                                    } else {
                                        log.error("Error when calling service listSlots: " + event.left().getValue());
                                        badRequest(request);
                                    }
                                }
                            });

                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }


    @ApiDoc("Update a given slot for a given slot profile")
    @Put("/slotprofiles/:idSlotProfile/slots/:idSlot")
    @SecuredAction(value = "directory.slot.manage")
    public void updateSlot(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createSlot", new Handler<JsonObject>() {
                        @Override
                        public void handle(final JsonObject jsonSlot) {
                            final String idSlotProfile = request.params().get("idSlotProfile");
                            final String idSlot = request.params().get("idSlot");
                            jsonSlot.put(Slot.ID, idSlot);
                            final Slot slot = new Slot(jsonSlot);
                            Long slotStart = slot.getStart();
                            Long slotEnd = slot.getEnd();
                            if (slotStart == null || slotEnd == null) {
                                String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                        "directory.slot.bad.request.format.hours.invalid",
                                        Renders.getHost(request),
                                        I18n.acceptLanguage(request));
                                badRequest(request, errorMessage);
                                return;
                            }
                            if (slotStart >= slotEnd) {
                                String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                        "directory.slot.bad.request.invalid.hours",
                                        Renders.getHost(request),
                                        I18n.acceptLanguage(request));
                                badRequest(request, errorMessage);
                                return;
                            }

                            slotProfileService.listSlots(idSlotProfile, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight()) {
                                        JsonObject value = event.right().getValue();
                                        JsonArray existingSlots = value.getJsonArray("slots");
                                        if (slotNameAlreadyExists(existingSlots, slot)) {
                                            String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                                    "directory.slot.bad.request.slot.name.already.exists",
                                                    Renders.getHost(request),
                                                    I18n.acceptLanguage(request));
                                            badRequest(request, errorMessage);
                                            return;
                                        }
                                        // check no overlapping
                                        if (overlapAnotherSlot(existingSlots, slot)) {
                                            String errorMessage = I18n.getInstance(config.getString("main")).translate(
                                                    "directory.slot.bad.request.slot.overlap",
                                                    Renders.getHost(request),
                                                    I18n.acceptLanguage(request));
                                            badRequest(request, errorMessage);
                                        } else {
                                            slotProfileService.updateSlot(idSlotProfile, jsonSlot, notEmptyResponseHandler(request));
                                        }
                                    } else {
                                        log.error("Error when calling service listSlots: " + event.left().getValue());
                                        badRequest(request);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @ApiDoc("Delete a given slot for a given slot profile")
    @Delete("/slotprofiles/:idSlotProfile/slots/:idSlot")
    @SecuredAction(value = "directory.slot.manage")
    public void deleteSlotFromSlotProfile(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String idSlotProfile = request.params().get("idSlotProfile");
                    final String idSlot = request.params().get("idSlot");
                    slotProfileService.deleteSlotFromSlotProfile(idSlotProfile, idSlot, defaultResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    private boolean slotNameAlreadyExists(JsonArray existingSlots, Slot slot) {
        if (existingSlots != null) {
            for (Object o : existingSlots) {
                if (!(o instanceof JsonObject)) {
                    continue;
                }
                JsonObject jo = (JsonObject) o;
                Slot existingSlot = new Slot(jo);
                String givenSlotId = slot.getId();

                if (!existingSlot.getId().equals(givenSlotId)
                        && existingSlot.getName().equals(slot.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean newSlotProfileNameCanByUsed(JsonArray existingSlotProfiles, JsonObject slotProfile) {
        String slotProfileName = slotProfile.getString("name", "");
        String slotProfileId = slotProfile.getString("_id", "");
        if (existingSlotProfiles != null) {
            for (Object o : existingSlotProfiles) {
                if (!(o instanceof JsonObject)) {
                    continue;
                }
                JsonObject jo = (JsonObject) o;
                // ignore comparison if it is the same slot id
                String existingSlotProfileId = jo.getString("_id", "");
                if (existingSlotProfileId.equals(slotProfileId)) {
                    continue;
                }
                String name = jo.getString("name", "");
                if (slotProfileName.equals(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean overlapAnotherSlot(JsonArray existingSlots, Slot slot) {
        if (existingSlots != null) {
            for (Object o : existingSlots) {
                if (!(o instanceof JsonObject)) {
                    continue;
                }
                JsonObject jo = (JsonObject) o;
                Slot existingSlot = new Slot(jo);
                String existingSlotId = existingSlot.getId();
                // ignore comparison if it is the same slot id
                if (existingSlotId.equals(slot.getId())) {
                    continue;
                }
                if (!existingSlot.getName().equals(slot.getName())
                        && slot.overlap(existingSlot)) {
                    return true;
                }
            }
        }
        return false;
    }

    @ApiDoc("Get all slot profiles for a school")
    @Get("/slotprofiles/schools/:schoolId")
    public void listSlotProfilesBySchool(HttpServerRequest request) {
        final String structureId = request.params().get("schoolId");
        if (structureId == null) {
            String errorMessage = I18n.getInstance(config.getString("main")).translate(
                    "directory.slot.bad.request.invalid.structure",
                    Renders.getHost(request),
                    I18n.acceptLanguage(request));
            badRequest(request, errorMessage);
            return;
        }

        slotProfileService.listSlotProfilesByStructure(structureId, arrayResponseHandler(request));
    }


    @ApiDoc("Get all slots for a slot profile")
    @Get("/slotprofiles/:idSlotProfile/slots")
    public void listSlotsInAProfile(HttpServerRequest request) {
        String idSlotProfile = request.params().get("idSlotProfile");
        slotProfileService.listSlots(idSlotProfile, notEmptyResponseHandler(request));
    }

    public void setSlotProfileService(SlotProfileService slotProfileService) {
        this.slotProfileService = slotProfileService;
    }

}
