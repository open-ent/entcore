/*
 * Copyright © "Open Digital Education", 2014
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package org.entcore.common.events.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.AsyncResult;
import org.entcore.common.events.EventStore;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.wseduc.webutils.Utils.getOrElse;

public abstract class GenericEventStore implements EventStore {

	protected String module;
	protected EventBus eventBus;
	protected Vertx vertx;
	protected JsonArray userBlacklist;
	protected static final Logger logger = LoggerFactory.getLogger(GenericEventStore.class);

	@Override
	public void createAndStoreEvent(String eventType, UserInfos user) {
		createAndStoreEvent(eventType, user, null);
	}

	@Override
	public void createAndStoreEvent(final String eventType, final HttpServerRequest request,
			final JsonObject customAttributes) {
		UserUtils.getUserInfos(eventBus, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				execute(user, eventType, request, customAttributes);
			}
		});
	}

	@Override
	public void createAndStoreEvent(String eventType, UserInfos user, JsonObject customAttributes) {
		execute(user, eventType, null, customAttributes);
	}

	@Override
	public void createAndStoreEvent(final String eventType, final HttpServerRequest request) {
		createAndStoreEvent(eventType, request, null);
	}

	@Override
	public void createAndStoreEvent(final String eventType, final String login) {
		createAndStoreEvent(eventType, login, (String) null);
	}

	@Override
	public void createAndStoreEvent(final String eventType, final String login, final String clientId) {
		createAndStoreEvent(eventType, "login", login, clientId, null);
	}

	@Override
	public void createAndStoreEventByUserId(final String eventType, final String userId, final String clientId) {
		createAndStoreEvent(eventType, "id", userId, clientId, null);
	}

	@Override
	public void createAndStoreEvent(String eventType, String login, HttpServerRequest request) {
		createAndStoreEvent(eventType, login, (String) null, request);
	}

	@Override
	public void createAndStoreEvent(String eventType, String login, String clientId, HttpServerRequest request) {
		createAndStoreEvent(eventType, "login", login, clientId, request);
	}

	@Override
	public void createAndStoreEventByUserId(String eventType, String userId, String clientId,
			HttpServerRequest request) {
		createAndStoreEvent(eventType, "id", userId, clientId, request);
	}

	@Override
	public void storeCustomEvent(String baseEventType, JsonObject payload) {

	}

	private void createAndStoreEvent(final String eventType, final String attr, final String value,
			final String clientId, final HttpServerRequest request) {
		final boolean isLoginEvent = "LOGIN".equals(eventType); // hack for security device change notification
		final String query = "MATCH (n:User {" + attr + ": {login}}) " + "OPTIONAL MATCH n-[:IN]->(gp:ProfileGroup) "
				+ "OPTIONAL MATCH gp-[:DEPENDS]->(s:Structure) " + "OPTIONAL MATCH gp-[:DEPENDS]->(c:Class) "
				+ (isLoginEvent? "OPTIONAL MATCH (n)-[r:HAS_FUNCTION]->(f:Function) " : "")
				+ "RETURN distinct n.id as userId,  head(n.profiles) as type, COLLECT(distinct gp.id) as profilGroupsIds, "
				+ "COLLECT(distinct c.id) as classes, COLLECT(distinct s.id) as structures"
				+ (isLoginEvent ? ", head(collect(distinct f.name)) as useradmin " : "");
		Neo4j.getInstance().execute(query, new JsonObject().put("login", value), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray res = event.body().getJsonArray("result");
				if ("ok".equals(event.body().getString("status")) && res.size() == 1) {
					JsonObject customAttributes = null;
					if (clientId != null) {
						customAttributes = new JsonObject();
						customAttributes.put("override-module", clientId);
					}
					if (isLoginEvent) { // hack for security device change notification
						final Object userAdmin = res.getJsonObject(0).remove("useradmin");
						if (userAdmin != null && !userAdmin.toString().isEmpty()) {
							if (customAttributes == null) {
								customAttributes = new JsonObject();
							}
							customAttributes.put("useradmin", userAdmin);
						}
					}
					execute(UserUtils.sessionToUserInfos(res.getJsonObject(0)), eventType, request, customAttributes);
				} else {
					if ("login".equals(attr)) {
						createAndStoreEvent(eventType, "loginAlias", value, clientId, request);
					} else {
						logger.error("Error : user " + value + " not found.");
					}
				}

			}
		});
	}

	private void execute(UserInfos user, String eventType, HttpServerRequest request, JsonObject customAttributes) {
		if (user == null || !userBlacklist.contains(user.getUserId())) {
			storeEvent(generateEvent(eventType, user, request, customAttributes), new Handler<Either<String, Void>>() {
				@Override
				public void handle(Either<String, Void> event) {
					if (event.isLeft()) {
						logger.error("Error adding event : " + event.left().getValue());
					}
				}
			});
		}
	}

	private JsonObject generateEvent(String eventType, UserInfos user, HttpServerRequest request,
			JsonObject customAttributes) {
		JsonObject event = new JsonObject();
		if (customAttributes != null && customAttributes.size() > 0) {
			event.mergeIn(customAttributes);
		}
		event.put("event-type", eventType).put("module", getOrElse(event.remove("override-module"), module, false))
				.put("date", System.currentTimeMillis());
		if (user != null) {
			event.put("userId", user.getUserId());
			if (user.getType() != null) {
				event.put("profil", user.getType());
			}
			if (user.getStructures() != null) {
				event.put("structures", new JsonArray(user.getStructures()));
			}
			if (user.getClasses() != null) {
				event.put("classes", new JsonArray(user.getClasses()));
			}
			if (user.getGroupsIds() != null) {
				event.put("groups", new JsonArray(user.getGroupsIds()));
			}
		}
		if (request != null) {
			// event.put("referer", request.headers().get("Referer"));
			// event.put("sessionId", CookieHelper.getInstance().getSigned("oneSessionId", request));
			final String ua = request.headers().get("User-Agent");
			if (ua != null) {
				event.put("ua", ua);
			}
			final String ip = Renders.getIp(request);
			if (ip != null) {
				event.put("ip", ip);
			}
		}
		return event;
	}

	protected abstract void storeEvent(JsonObject event, Handler<Either<String, Void>> handler);

	private void initBlacklist() {
		eventBus.request("event.blacklist", new JsonObject(), (Handler<AsyncResult<Message<JsonArray>>>) message -> {
	      if (message.succeeded()) {
	        userBlacklist = message.result().body();
	      } else {
	        userBlacklist = new JsonArray();
	      }
		});
	}

	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
		this.initBlacklist();
	}

	public void setModule(String module) {
		this.module = module;
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

}
