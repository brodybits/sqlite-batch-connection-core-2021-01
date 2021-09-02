#import "SQLiteBatchCore.h"

#include "sqlite-connection-core.h"

@implementation SQLiteBatchCore

static int bindObjectValue(int connection_id, NSObject * value, int index)
{
  int prepareResult;

  if (value == nil) {
    prepareResult = scc_bind_null(connection_id, index);
  } else if ([value isKindOfClass: [NSNumber class]]) {
    prepareResult =
      scc_bind_double(connection_id, index, [(NSNumber *)value doubleValue]);
  } else if ([value isKindOfClass: [NSString class]]) {
    prepareResult =
      scc_bind_text(connection_id, index, [(NSString *)value cString]);
  } else {
    prepareResult = scc_bind_null(connection_id, index);
  }

  return prepareResult;
}

+ (void) initialize
{
  scc_init();
}

+ (int) openBatchConnection: (NSString *) fullName
                      flags: (int) flags
{
  return scc_open_connection([fullName cString], flags);
}

+ (NSArray *) executeBatch: (int) connection_id
                      data: (NSArray *) data
{
  NSMutableArray * results = [NSMutableArray arrayWithCapacity: 0];

  for (int i=0; i < [data count]; ++i) {
    int previousTotalChanges = scc_get_total_changes(connection_id);

    NSArray * entry = [data objectAtIndex:i];

    NSString * statement = [entry objectAtIndex: 0];

    int prepareResult =
      scc_begin_statement(connection_id, [statement cString]);

    if (prepareResult != 0) {
      // do nothing here; error will be handled below
    } else if ([[entry objectAtIndex: 1] isKindOfClass: [NSDictionary class]]) {
      NSDictionary * parameters = [entry objectAtIndex: 1];

      for (NSString * key in parameters) {
        // NOTE: zero parameter index will result in a bind error below.
        int parameter_index =
          scc_bind_parameter_index(connection_id, [key cString]);

        NSObject * bindValue = [parameters valueForKey: key];

        prepareResult =
          bindObjectValue(connection_id, bindValue, parameter_index);

	// break here & report error in case a named parameter is not found
        if (prepareResult != 0) break;
      }
    } else {
      NSArray * bind = [entry objectAtIndex: 1];

      for (int j=0; j < [bind count]; ++j) {
        NSObject * bindValue = [bind objectAtIndex: j];

        prepareResult = bindObjectValue(connection_id, bindValue, 1 + j);

        // should only break if there are too many parameters here
        if (prepareResult != 0) break;
      }
    }

    if (prepareResult != 0) {
      [results addObject: @{
        @"status": @1,
        @"message": [NSString stringWithUTF8String:
          scc_get_last_error_message(connection_id)]
      }];

      scc_end_statement(connection_id);

      continue;
    }

    int stepResult = scc_step(connection_id);

    if (stepResult == 100) {
      const int columnCount = scc_get_column_count(connection_id);

      NSMutableArray * columns = [NSMutableArray arrayWithCapacity: 0];

      for (int j = 0; j < columnCount; ++j) {
        NSString * columnNameAsString =
          [NSString stringWithUTF8String: scc_get_column_name(connection_id, j)];
        [columns addObject: columnNameAsString];
      }

      NSMutableArray * rows = [NSMutableArray arrayWithCapacity: 0];

      do {
        NSMutableArray * row = [NSMutableArray arrayWithCapacity: 0];

        for (int j = 0; j < columnCount; ++j) {
          const int columnType = scc_get_column_type(connection_id, j);

          if (columnType == SCC_COLUMN_TYPE_INTEGER ||
              columnType == SCC_COLUMN_TYPE_FLOAT) {
            NSNumber * columnNumberValue =
              [NSNumber numberWithDouble: scc_get_column_double(connection_id, j)];
            [row addObject: columnNumberValue];
          } else if (columnType == SCC_COLUMN_TYPE_NULL) {
            [row addObject: [NSNull null]];
          } else {
            NSString * columnStringValue =
              [NSString stringWithUTF8String: scc_get_column_text(connection_id, j)];
            [row addObject: columnStringValue];
          }
        }

        [rows addObject: row];

        stepResult = scc_step(connection_id);
      } while(stepResult == 100);

      [results addObject: @{@"status":@0, @"columns": columns, @"rows": rows}];
    } else if (stepResult == 101) {
      int totalChanges = scc_get_total_changes(connection_id);
      int rowsAffected = totalChanges - previousTotalChanges;

      [results addObject: @{
        @"status": @0,
        @"rowsAffected": [NSNumber numberWithInteger: rowsAffected],
        @"totalChanges": [NSNumber numberWithInteger: totalChanges],
        @"lastInsertRowId": [NSNumber numberWithInteger:
          scc_get_last_insert_rowid(connection_id)]
      }];
    } else {
      [results addObject: @{
        @"status": @1,
        @"message": [NSString stringWithUTF8String:
          scc_get_last_error_message(connection_id)]
      }];
    }

    scc_end_statement(connection_id);
  }

  return results;
}

@end
