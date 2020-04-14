/*
 * Copyright © "Open Digital Education", 2016
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

package org.entcore.registry.services;

import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface ExternalApplicationService {

	void listExternalApps(String structureId, Handler<Either<String, JsonArray>> handler);
	void listExternalApplicationRolesWithGroups(String structureId, String connectorId,  Handler<Either<String, JsonArray>> handler);
	void deleteExternalApplication(String applicationId, Handler<Either<String, JsonObject>> handler);
	void createExternalApplication(String structureId, JsonObject application, Handler<Either<String, JsonObject>> handler);
	void toggleLock(String structureId, Handler<Either<String, JsonObject>> handler);
	void massAuthorize(String structureId, String appId, List<String> profiles, Handler<Either<String, JsonObject>> handler);
	void massUnauthorize(String structureId, String appId, List<String> profiles, Handler<Either<String, JsonObject>> handler);

}
