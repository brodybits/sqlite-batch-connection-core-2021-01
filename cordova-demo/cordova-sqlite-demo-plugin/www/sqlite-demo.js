/* for prettier-standard --lint (eslint): */
/* global cordova */

function openDatabaseConnection (options, cb, errorCallback) {
  cordova.exec(
    cb,
    errorCallback,
    'SQLiteDemoPlugin',
    'openDatabaseConnection',
    [options]
  )
}

function executeBatch (connectionId, batchList, cb) {
  cordova.exec(cb, null, 'SQLiteDemoPlugin', 'executeBatch', [
    connectionId,
    // avoid potential behavior such as crashing in case of invalid
    // batchList due to possible API abuse
    batchList.map(function (entry) {
      return [
        entry[0],
        Array.isArray(entry[1])
          ? entry[1].map(function (parameter) {
              return parameter
            })
          : Object.assign({}, entry[1])
      ]
    })
  ])
}

window.sqliteBatchConnection = {
  openDatabaseConnection: openDatabaseConnection,
  executeBatch: executeBatch
}
