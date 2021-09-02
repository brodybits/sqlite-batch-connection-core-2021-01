package io.sqlc;

import org.json.JSONArray;
import org.json.JSONObject;

public class SQLiteBatchCore {
  static public int openBatchConnection(String fullName, int flags) {
    return SCCoreGlue.scc_open_connection(fullName, flags);
  }

  static public JSONArray executeBatch(int mydbc, JSONArray data) {
    try {
      final int count = data.length();

      JSONArray results = new JSONArray();

      for (int i=0; i<count; ++i) {
        int previousTotalChanges = SCCoreGlue.scc_get_total_changes(mydbc);

        JSONArray entry = data.getJSONArray(i);

        String s = entry.getString(0);

        if (SCCoreGlue.scc_begin_statement(mydbc, s) != 0) {
          JSONObject result = new JSONObject();
          result.put("status", 1); // REPORT SQLite ERROR 1
          result.put("message", SCCoreGlue.scc_get_last_error_message(mydbc));
          results.put(result);
        } else {
          int bindResult = 0; // SQLite OK

          if ((entry.get(1) instanceof JSONArray)) {
            final JSONArray bind = entry.getJSONArray(1);
            final int bindCount = bind.length();

            for (int j = 0; j < bindCount; ++j) {
              final Object o = bind.get(j);

              bindResult = bindObjectValue(mydbc, o, 1 + j);

              // should only break if there are too many parameters here
              if (bindResult != 0) break;
            }
          } else {
            final JSONObject bind = entry.getJSONObject(1);
            final int bindCount = bind.length();
            final JSONArray names = bind.names();

            for (int j = 0; j < bindCount; ++j) {
              final String name = names.getString(j);
              final Object o = bind.get(name);
              final int bindIndex =
                SCCoreGlue.scc_bind_parameter_index(mydbc, name);

              bindResult = bindObjectValue(mydbc, o, bindIndex);

              // break here & report error if a named parameter is not found
              if (bindResult != 0) break;
            }
          }

          if (bindResult != 0) {
            JSONObject result = new JSONObject();
            result.put("status", 1); // REPORT SQLite ERROR 1
            result.put("message", SCCoreGlue.scc_get_last_error_message(mydbc));
            results.put(result);
            SCCoreGlue.scc_end_statement(mydbc);
            continue;
          }

          int stepResult = SCCoreGlue.scc_step(mydbc);

          if (stepResult == 100) {
            final int columnCount = SCCoreGlue.scc_get_column_count(mydbc);

            JSONArray columns = new JSONArray();

            for (int j=0; j < columnCount; ++j) {
              columns.put(SCCoreGlue.scc_get_column_name(mydbc, j));
            }

            JSONArray rows = new JSONArray();

            do {
              JSONArray row = new JSONArray();

              for (int col=0; col < columnCount; ++col) {
                final int type = SCCoreGlue.scc_get_column_type(mydbc, col);

                if (type == SCCoreGlue.SCC_COLUMN_TYPE_INTEGER ||
                    type == SCCoreGlue.SCC_COLUMN_TYPE_FLOAT) {
                  row.put(SCCoreGlue.scc_get_column_double(mydbc, col));
                } else if (type == SCCoreGlue.SCC_COLUMN_TYPE_NULL) {
                  row.put(JSONObject.NULL);
                } else {
                  row.put(SCCoreGlue.scc_get_column_text(mydbc, col));
                }
              }

              rows.put(row);

              stepResult = SCCoreGlue.scc_step(mydbc);
            } while (stepResult == 100);

            JSONObject result = new JSONObject();
            result.put("status", 0); // REPORT SQLite OK
            result.put("columns", columns);
            result.put("rows", rows);
            results.put(result);
            SCCoreGlue.scc_end_statement(mydbc);
          } else if (stepResult == 101) {
            int totalChanges = SCCoreGlue.scc_get_total_changes(mydbc);
            int rowsAffected = totalChanges - previousTotalChanges;

            JSONObject result = new JSONObject();
            // same order as iOS & macOS ("osx"):
            result.put("status", 0); // REPORT SQLite OK
            result.put("totalChanges", totalChanges);
            result.put("rowsAffected", rowsAffected);
            result.put("lastInsertRowId",
              SCCoreGlue.scc_get_last_insert_rowid(mydbc));
            results.put(result);
            SCCoreGlue.scc_end_statement(mydbc);
          } else {
            JSONObject result = new JSONObject();
            result.put("status", 1); // REPORT SQLite ERROR 1
            result.put("message", SCCoreGlue.scc_get_last_error_message(mydbc));
            results.put(result);
            SCCoreGlue.scc_end_statement(mydbc);
          }
        }
      }

      return results;
    } catch(Exception e) {
      // NOT EXPECTED - internal error:
      throw new RuntimeException(e);
    }
  }

  static private int bindObjectValue(int mydbc, Object value, int index)
  {
    int bindResult = 0; // SQLite OK

    if (value instanceof Number) {
      bindResult =
        SCCoreGlue.scc_bind_double(mydbc, index, ((Number)value).doubleValue());
    } else if (value instanceof String) {
      bindResult = SCCoreGlue.scc_bind_text(mydbc, index, value.toString());
    } else {
      bindResult = SCCoreGlue.scc_bind_null(mydbc, index);
    }
    return bindResult;
  }

  static {
    System.loadLibrary("sqlc-connection-core-glue");
    SCCoreGlue.scc_init();
  }
}
