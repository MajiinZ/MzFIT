import Foundation
import SQLite3
import TrainingApp

/// Separate versioned aggregate records, each replaced atomically by SQLite.
/// Only called through the Kotlin storage adapters on a background dispatcher.
final class SQLiteDocumentStore: NSObject, IosDocumentStore {
    private let lock = NSLock()
    private var database: OpaquePointer?
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private func open() throws -> OpaquePointer {
        if let database { return database }
        let directory = try FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var privateDirectory = directory
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try privateDirectory.setResourceValues(values)
        let path = directory.appendingPathComponent("training.sqlite").path
        var db: OpaquePointer?
        guard sqlite3_open_v2(path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK,
              let db else {
            if let db { sqlite3_close(db) }
            throw failure("Could not open local database")
        }
        do {
            sqlite3_busy_timeout(db, 5_000)
            try execute(db, "PRAGMA journal_mode=WAL")
            try execute(db, "PRAGMA synchronous=FULL")
            var version: OpaquePointer?
            guard sqlite3_prepare_v2(db, "PRAGMA user_version", -1, &version, nil) == SQLITE_OK else { throw failure("Cannot read database version") }
            defer { sqlite3_finalize(version) }
            guard sqlite3_step(version) == SQLITE_ROW else { throw failure("Cannot read database version") }
            let schema = sqlite3_column_int(version, 0)
            guard schema <= 1 else { throw failure("Database requires a newer app") }
            try execute(db, "CREATE TABLE IF NOT EXISTS documents (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            try execute(db, "PRAGMA user_version=1")
            database = db
            return db
        } catch { sqlite3_close(db); throw error }
    }

    func read(key: String) -> IosDocumentResult {
        lock.lock(); defer { lock.unlock() }
        do {
            let db = try open(); var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, "SELECT value FROM documents WHERE key=?", -1, &statement, nil) == SQLITE_OK else { throw failure("Read prepare failed") }
            defer { sqlite3_finalize(statement) }
            guard sqlite3_bind_text(statement, 1, key, -1, transient) == SQLITE_OK else { throw failure("Read bind failed") }
            let code = sqlite3_step(statement)
            if code == SQLITE_DONE { return IosDocumentResult(value: nil, error: nil) }
            guard code == SQLITE_ROW, let bytes = sqlite3_column_text(statement, 0) else { throw failure("Read failed") }
            let count = Int(sqlite3_column_bytes(statement, 0))
            guard let value = String(bytes: UnsafeBufferPointer(start: bytes, count: count), encoding: .utf8) else { throw failure("Invalid local record encoding") }
            return IosDocumentResult(value: value, error: nil)
        } catch { return IosDocumentResult(value: nil, error: error.localizedDescription) }
    }

    func write(key: String, value: String) -> String? {
        lock.lock(); defer { lock.unlock() }
        do {
            let db = try open(); var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, "INSERT INTO documents(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", -1, &statement, nil) == SQLITE_OK else { throw failure("Save prepare failed") }
            defer { sqlite3_finalize(statement) }
            guard sqlite3_bind_text(statement, 1, key, -1, transient) == SQLITE_OK,
                  sqlite3_bind_text(statement, 2, value, Int32(value.utf8.count), transient) == SQLITE_OK,
                  sqlite3_step(statement) == SQLITE_DONE else { throw failure("Save failed") }
            return nil
        } catch { return error.localizedDescription }
    }
    private func execute(_ db: OpaquePointer, _ sql: String) throws {
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw failure("Database setup failed") }
    }
    private func failure(_ message: String) -> NSError { NSError(domain: "MLPT.Storage", code: 1, userInfo: [NSLocalizedDescriptionKey: message]) }
    deinit { if let database { sqlite3_close(database) } }
}
