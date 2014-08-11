/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.common.share.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.security.SecuredAction;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public class SqlShareService extends GenericShareService {

	private final Sql sql;
	private final String schema;
	private final String shareTable;

	public SqlShareService(EventBus eb,
			Map<String, SecuredAction> securedActions, Map<String, List<String>> groupedActions) {
		this(null, null, eb, securedActions, groupedActions);
	}

	public SqlShareService(String schema, String shareTable, EventBus eb,
			Map<String, SecuredAction> securedActions, Map<String, List<String>> groupedActions) {
		super(eb, securedActions, groupedActions);
		sql = Sql.getInstance();
		this.schema = (schema != null && !schema.trim().isEmpty()) ? schema + "." : "";
		this.shareTable = this.schema+((shareTable != null && !shareTable.trim().isEmpty()) ? shareTable : "shares");
	}

	@Override
	public void shareInfos(final String userId, String resourceId, final String acceptLanguage,
			final Handler<Either<String, JsonObject>> handler) {
		if (userId == null || userId.trim().isEmpty()) {
			handler.handle(new Either.Left<String, JsonObject>("Invalid userId."));
			return;
		}
		if (resourceId == null || resourceId.trim().isEmpty()) {
			handler.handle(new Either.Left<String, JsonObject>("Invalid resourceId."));
			return;
		}
		final JsonArray actions = getResoureActions(securedActions);
		String query = "SELECT member_id, action FROM " + shareTable + " WHERE resource_id = ?";
		sql.prepared(query, new JsonArray().add(Sql.parseId(resourceId)), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				if ("ok".equals(message.body().getString("status"))) {
					JsonArray f = message.body().getArray("fields");
					int memberIdx, actionIdx;
					if ("member_id".equals(f.get(0))) {
						memberIdx = 0;
						actionIdx = 1;
					} else {
						memberIdx = 1;
						actionIdx = 0;
					}
					JsonArray r = message.body().getArray("results");
					JsonObject checkedActions = new JsonObject();
					for (Object o : r) {
						if (!(o instanceof JsonArray)) continue;
						JsonArray row = (JsonArray) o;
						String memberId = row.get(memberIdx);
						JsonArray m = checkedActions.getArray(memberId);
						if (m == null) {
							m = new JsonArray();
							checkedActions.putArray(memberId, m);
						}
						m.add(row.get(actionIdx));
					}
					getShareInfos(userId, actions, checkedActions, acceptLanguage, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject event) {
							if (event != null && event.size() == 3) {
								handler.handle(new Either.Right<String, JsonObject>(event));
							} else {
								handler.handle(new Either.Left<String, JsonObject>(
										"Error finding shared resource."));
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void groupShare(String userId, final String groupShareId, final String resourceId,
			final List<String> actions, final Handler<Either<String, JsonObject>> handler) {
		groupShareValidation(userId, groupShareId, actions, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					share(resourceId, groupShareId, actions, "groups", handler);
				} else {
					handler.handle(event);
				}
			}
		});
	}

	@Override
	public void userShare(String userId, final String userShareId, final String resourceId,
			final List<String> actions, final Handler<Either<String, JsonObject>> handler) {
		userShareValidation(userId, userShareId, actions, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					share(resourceId, userShareId, actions, "users", handler);
				} else {
					handler.handle(event);
				}
			}
		});
	}

	@Override
	public void removeGroupShare(String groupId, String resourceId,
			List<String> actions, Handler<Either<String, JsonObject>> handler) {
		removeShare(resourceId, groupId, actions, handler);
	}

	@Override
	public void removeUserShare(String userId, String resourceId,
			List<String> actions, Handler<Either<String, JsonObject>> handler) {
		removeShare(resourceId, userId, actions, handler);
	}

	private void removeShare(String resourceId, String userId, List<String> actions,
			Handler<Either<String, JsonObject>> handler) {
		Object[] a = actions.toArray();
		String query = "DELETE FROM " + shareTable + " WHERE action IN " + Sql.listPrepared(a) +
				" AND resource_id = ? AND member_id = ?";
		JsonArray values = new JsonArray(a).add(Sql.parseId(resourceId)).add(userId);
		sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
	}

	private void share(String resourceId, final String shareId, List<String> actions,
			final String membersTable, final Handler<Either<String, JsonObject>> handler) {
		final SqlStatementsBuilder s = new SqlStatementsBuilder();
		s.raw("LOCK TABLE " + schema + membersTable + " IN SHARE ROW EXCLUSIVE MODE");
		s.raw(
				"INSERT INTO " + schema + membersTable + " (id) SELECT '" + shareId +
				"' WHERE NOT EXISTS (SELECT * FROM " + schema + membersTable + " WHERE id='" + shareId + "');"
		);
		JsonArray a = new JsonArray();
		final Object rId = Sql.parseId(resourceId);
		for (String action : actions) {
			JsonArray ar = new JsonArray()
					.add(shareId).add(rId).add(action);
			a.add(ar);
		}
		s.insert(shareTable, new JsonArray().add("member_id").add("resource_id").add("action"), a);
		sql.prepared("SELECT count(*) FROM " + shareTable + " WHERE member_id = ? AND resource_id = ?",
				new JsonArray().add(shareId).add(Sql.parseId(resourceId)), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(final Message<JsonObject> message) {
				final Long nb = SqlResult.countResult(message);
				sql.transaction(s.build(), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> res) {
						Either<String, JsonObject> r = SqlResult.validUniqueResult(2, res);
						if (r.isRight() && nb == 0) {
							JsonObject notify = new JsonObject();
							notify.putString(membersTable.substring(0, membersTable.length() - 1) + "Id", shareId);
							r.right().getValue().putObject("notify-timeline", notify);
						}
						handler.handle(r);
					}
				});
			}
		});
	}

}
