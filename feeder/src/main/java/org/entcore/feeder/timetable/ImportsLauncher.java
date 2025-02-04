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

package org.entcore.feeder.timetable;

import org.entcore.common.storage.Storage;
import org.entcore.feeder.dictionary.structures.PostImport;
import org.entcore.feeder.timetable.edt.EDTImporter;
import org.entcore.feeder.timetable.edt.EDTUtils;
import org.entcore.feeder.timetable.udt.UDTImporter;
import org.entcore.feeder.utils.ResultMessage;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportsLauncher implements Handler<Long> {

	protected static final Logger log = LoggerFactory.getLogger(ImportsLauncher.class);
	protected static final Pattern UAI_PATTERN = Pattern.compile(".*([0-9]{7}[A-Z]).*");
	protected final Vertx vertx;
	protected final JsonObject config;
	protected final Storage storage;
	protected String path;
	protected final PostImport postImport;
	protected EDTUtils edtUtils;
	protected final boolean timetableUserCreation;
	protected final boolean isManualImport;

	public ImportsLauncher(Vertx vertx, JsonObject config, Storage storage, String path, PostImport postImport, boolean timetableUserCreation, boolean isManualImport) {
		this.vertx = vertx;
		this.config = config;
		this.storage = storage;
		this.path = path;
		this.postImport = postImport;
		this.timetableUserCreation = timetableUserCreation;
		this.isManualImport = isManualImport;
	}

	public ImportsLauncher(Vertx vertx, JsonObject config, Storage storage, String path, PostImport postImport, EDTUtils edtUtils,
			boolean timetableUserCreation, boolean isManualImport) {
		this(vertx, config, storage, path, postImport, timetableUserCreation, isManualImport);
		this.edtUtils = edtUtils;
	}

	public void setPath(String path)
	{
		this.path = path;
	}

	@Override
	public void handle(Long event) {
		listFiles(new Handler<AsyncResult<List<String>>>() {
			@Override
			public void handle(final AsyncResult<List<String>> event) {
				if (event.succeeded()) {
					int nbFiles = event.result().size();
					final Handler[] handlers = new Handler[nbFiles + 1];
					handlers[handlers.length -1] = new Handler<Void>() {
						@Override
						public void handle(Void v) {
							if(nbFiles > 0)
								postImport.execute();
						}
					};
					Collections.sort(event.result());
					for (int i = nbFiles - 1; i >= 0; i--) {
						final int j = i;
						handlers[i] = new Handler<Void>() {
							@Override
							public void handle(Void v) {
								importFile(event.result().get(j), handlers[j + 1]);
							}
						};
					}
					handlers[0].handle(null);
				} else {
					log.error("Error reading files.");
				}
			}
		});
	}

	protected void listFiles(Handler<AsyncResult<List<String>>> handler)
	{
		vertx.fileSystem().readDir(path, (edtUtils != null ? ".*.xml": ".*.zip"), handler);
	}

	protected void importFile(String file, Handler<Void> handler)
	{
		log.info("Parsing file " + file);
		Matcher matcher;
		if (file != null && (matcher = UAI_PATTERN.matcher(file)).find()) {

			ResultMessage m = new ResultMessage(new Handler<JsonObject>() {
				@Override
				public void handle(JsonObject event) {
					if (!"ok".equals(event.getString("status"))) {
						log.error("Error in import : " + file + " - " + event.getString("message"));
					}
					handler.handle(null);
				}
			})
					.put("path", file)
					.put("UAI", matcher.group(1))
					.put("isManualImport", isManualImport)
					.put("language", "fr");
			if (edtUtils != null) {
				EDTImporter.launchImport(vertx, config, storage, edtUtils, m, timetableUserCreation);
			} else {
				UDTImporter.launchImport(vertx, config, storage, m, timetableUserCreation);
			}
		} else {
			log.error("UAI not found in filename " + file);
		}
	}
}
