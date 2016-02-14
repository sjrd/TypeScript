package be.doeraene.tsc

/// <reference path="core.ts"/>

object Sys {
  type FileWatcherCallback = (path: String, removed?: Boolean) => Unit
  type DirectoryWatcherCallback = (path: String) => Unit

  trait System {
    args: String[]
    newLine: String
    useCaseSensitiveFileNames: Boolean
    write(s: String): Unit
    readFile(path: String, encoding?: String): String
    writeFile(path: String, data: String, writeByteOrderMark?: Boolean): Unit
    watchFile?(path: Path, callback: FileWatcherCallback): FileWatcher
    watchDirectory?(path: String, callback: DirectoryWatcherCallback, recursive?: Boolean): FileWatcher
    resolvePath(path: String): String
    fileExists(path: String): Boolean
    directoryExists(path: String): Boolean
    createDirectory(path: String): Unit
    getExecutingFilePath(): String
    getCurrentDirectory(): String
    readDirectory(path: String, extension?: String, exclude?: String[]): String[]
    getMemoryUsage?(): Int
    exit(exitCode?: Int): Unit
  }

  trait WatchedFile {
    filePath: Path
    callback: FileWatcherCallback
    mtime?: Date
  }

  trait FileWatcher {
    close(): Unit
  }

  trait DirectoryWatcher extends FileWatcher {
    directoryPath: Path
    referenceCount: Int
  }

  declare var require: any
  declare var module: any
  declare var process: any
  declare var global: any
  declare var __filename: String
  declare var Buffer: {
    new (str: String, encoding?: String): any
  }

  declare class Enumerator {
    public atEnd(): Boolean
    public moveNext(): Boolean
    public item(): any
    constructor(o: any)
  }

  declare var ChakraHost: {
    args: String[]
    currentDirectory: String
    executingFile: String
    newLine?: String
    useCaseSensitiveFileNames?: Boolean
    echo(s: String): Unit
    quit(exitCode?: Int): Unit
    fileExists(path: String): Boolean
    directoryExists(path: String): Boolean
    createDirectory(path: String): Unit
    resolvePath(path: String): String
    readFile(path: String): String
    writeFile(path: String, contents: String): Unit
    readDirectory(path: String, extension?: String, exclude?: String[]): String[]
    watchFile?(path: String, callback: FileWatcherCallback): FileWatcher
    watchDirectory?(path: String, callback: DirectoryWatcherCallback, recursive?: Boolean): FileWatcher
  }

  var sys: System = (def () {

    def getWScriptSystem(): System {

      val fso = new ActiveXObject("Scripting.FileSystemObject")

      val fileStream = new ActiveXObject("ADODB.Stream")
      fileStream.Type = 2 /*text*/

      val binaryStream = new ActiveXObject("ADODB.Stream")
      binaryStream.Type = 1 /*binary*/

      val args: String[] = []
      for (var i = 0; i < WScript.Arguments.length; i++) {
        args[i] = WScript.Arguments.Item(i)
      }

      def readFile(fileName: String, encoding?: String): String {
        if (!fso.FileExists(fileName)) {
          return ()
        }
        fileStream.Open()
        try {
          if (encoding) {
            fileStream.Charset = encoding
            fileStream.LoadFromFile(fileName)
          }
          else {
            // Load file and read the first two bytes into a String with no interpretation
            fileStream.Charset = "x-ansi"
            fileStream.LoadFromFile(fileName)
            val bom = fileStream.ReadText(2) || ""
            // Position must be at 0 before encoding can be changed
            fileStream.Position = 0
            // [0xFF,0xFE] and [0xFE,0xFF] mean utf-16 (little or big endian), otherwise default to utf-8
            fileStream.Charset = bom.length >= 2 && (bom.charCodeAt(0) == 0xFF && bom.charCodeAt(1) == 0xFE || bom.charCodeAt(0) == 0xFE && bom.charCodeAt(1) == 0xFF) ? "unicode" : "utf-8"
          }
          // ReadText method always strips byte order mark from resulting String
          return fileStream.ReadText()
        }
        catch (e) {
          throw e
        }
        finally {
          fileStream.Close()
        }
      }

      def writeFile(fileName: String, data: String, writeByteOrderMark?: Boolean): Unit {
        fileStream.Open()
        binaryStream.Open()
        try {
          // Write characters in UTF-8 encoding
          fileStream.Charset = "utf-8"
          fileStream.WriteText(data)
          // If we don't want the BOM, then skip it by setting the starting location to 3 (size of BOM).
          // If not, start from position 0, as the BOM will be added automatically when charset==utf8.
          if (writeByteOrderMark) {
            fileStream.Position = 0
          }
          else {
            fileStream.Position = 3
          }
          fileStream.CopyTo(binaryStream)
          binaryStream.SaveToFile(fileName, 2 /*overwrite*/)
        }
        finally {
          binaryStream.Close()
          fileStream.Close()
        }
      }

      def getCanonicalPath(path: String): String {
        return path.toLowerCase()
      }

      def getNames(collection: any): String[] {
        val result: String[] = []
        for (var e = new Enumerator(collection); !e.atEnd(); e.moveNext()) {
          result.push(e.item().Name)
        }
        return result.sort()
      }

      def readDirectory(path: String, extension?: String, exclude?: String[]): String[] {
        val result: String[] = []
        exclude = map(exclude, s => getCanonicalPath(combinePaths(path, s)))
        visitDirectory(path)
        return result
        def visitDirectory(path: String) {
          val folder = fso.GetFolder(path || ".")
          val files = getNames(folder.files)
          for (val current of files) {
            val name = combinePaths(path, current)
            if ((!extension || fileExtensionIs(name, extension)) && !contains(exclude, getCanonicalPath(name))) {
              result.push(name)
            }
          }
          val subfolders = getNames(folder.subfolders)
          for (val current of subfolders) {
            val name = combinePaths(path, current)
            if (!contains(exclude, getCanonicalPath(name))) {
              visitDirectory(name)
            }
          }
        }
      }

      return {
        args,
        newLine: "\r\n",
        useCaseSensitiveFileNames: false,
        write(s: String): Unit {
          WScript.StdOut.Write(s)
        },
        readFile,
        writeFile,
        resolvePath(path: String): String {
          return fso.GetAbsolutePathName(path)
        },
        fileExists(path: String): Boolean {
          return fso.FileExists(path)
        },
        directoryExists(path: String) {
          return fso.FolderExists(path)
        },
        createDirectory(directoryName: String) {
          if (!this.directoryExists(directoryName)) {
            fso.CreateFolder(directoryName)
          }
        },
        getExecutingFilePath() {
          return WScript.ScriptFullName
        },
        getCurrentDirectory() {
          return new ActiveXObject("WScript.Shell").CurrentDirectory
        },
        readDirectory,
        exit(exitCode?: Int): Unit {
          try {
            WScript.Quit(exitCode)
          }
          catch (e) {
          }
        }
      }
    }

    def getNodeSystem(): System {
      val _fs = require("fs")
      val _path = require("path")
      val _os = require("os")

      // average async stat takes about 30 microseconds
      // set chunk size to do 30 files in < 1 millisecond
      def createPollingWatchedFileSet(interval = 2500, chunkSize = 30) {
        var watchedFiles: WatchedFile[] = []
        var nextFileToCheck = 0
        var watchTimer: any

        def getModifiedTime(fileName: String): Date {
          return _fs.statSync(fileName).mtime
        }

        def poll(checkedIndex: Int) {
          val watchedFile = watchedFiles[checkedIndex]
          if (!watchedFile) {
            return
          }

          _fs.stat(watchedFile.filePath, (err: any, stats: any) => {
            if (err) {
              watchedFile.callback(watchedFile.filePath)
            }
            else if (watchedFile.mtime.getTime() != stats.mtime.getTime()) {
              watchedFile.mtime = getModifiedTime(watchedFile.filePath)
              watchedFile.callback(watchedFile.filePath, watchedFile.mtime.getTime() == 0)
            }
          })
        }

        // this implementation uses polling and
        // stat due to inconsistencies of fs.watch
        // and efficiency of stat on modern filesystems
        def startWatchTimer() {
          watchTimer = setInterval(() => {
            var count = 0
            var nextToCheck = nextFileToCheck
            var firstCheck = -1
            while ((count < chunkSize) && (nextToCheck != firstCheck)) {
              poll(nextToCheck)
              if (firstCheck < 0) {
                firstCheck = nextToCheck
              }
              nextToCheck++
              if (nextToCheck == watchedFiles.length) {
                nextToCheck = 0
              }
              count++
            }
            nextFileToCheck = nextToCheck
          }, interval)
        }

        def addFile(filePath: Path, callback: FileWatcherCallback): WatchedFile {
          val file: WatchedFile = {
            filePath,
            callback,
            mtime: getModifiedTime(filePath)
          }

          watchedFiles.push(file)
          if (watchedFiles.length == 1) {
            startWatchTimer()
          }
          return file
        }

        def removeFile(file: WatchedFile) {
          watchedFiles = copyListRemovingItem(file, watchedFiles)
        }

        return {
          getModifiedTime: getModifiedTime,
          poll: poll,
          startWatchTimer: startWatchTimer,
          addFile: addFile,
          removeFile: removeFile
        }
      }

      def createWatchedFileSet() {
        val dirWatchers = createFileMap<DirectoryWatcher>()
        // One file can have multiple watchers
        val fileWatcherCallbacks = createFileMap<FileWatcherCallback[]>()
        return { addFile, removeFile }

        def reduceDirWatcherRefCountForFile(filePath: Path) {
          val dirPath = getDirectoryPath(filePath)
          if (dirWatchers.contains(dirPath)) {
            val watcher = dirWatchers.get(dirPath)
            watcher.referenceCount -= 1
            if (watcher.referenceCount <= 0) {
              watcher.close()
              dirWatchers.remove(dirPath)
            }
          }
        }

        def addDirWatcher(dirPath: Path): Unit {
          if (dirWatchers.contains(dirPath)) {
            val watcher = dirWatchers.get(dirPath)
            watcher.referenceCount += 1
            return
          }

          val watcher: DirectoryWatcher = _fs.watch(
            dirPath,
            { persistent: true },
            (eventName: String, relativeFileName: String) => fileEventHandler(eventName, relativeFileName, dirPath)
          )
          watcher.referenceCount = 1
          dirWatchers.set(dirPath, watcher)
          return
        }

        def addFileWatcherCallback(filePath: Path, callback: FileWatcherCallback): Unit {
          if (fileWatcherCallbacks.contains(filePath)) {
            fileWatcherCallbacks.get(filePath).push(callback)
          }
          else {
            fileWatcherCallbacks.set(filePath, [callback])
          }
        }

        def addFile(filePath: Path, callback: FileWatcherCallback): WatchedFile {
          addFileWatcherCallback(filePath, callback)
          addDirWatcher(getDirectoryPath(filePath))

          return { filePath, callback }
        }

        def removeFile(watchedFile: WatchedFile) {
          removeFileWatcherCallback(watchedFile.filePath, watchedFile.callback)
          reduceDirWatcherRefCountForFile(watchedFile.filePath)
        }

        def removeFileWatcherCallback(filePath: Path, callback: FileWatcherCallback) {
          if (fileWatcherCallbacks.contains(filePath)) {
            val newCallbacks = copyListRemovingItem(callback, fileWatcherCallbacks.get(filePath))
            if (newCallbacks.length == 0) {
              fileWatcherCallbacks.remove(filePath)
            }
            else {
              fileWatcherCallbacks.set(filePath, newCallbacks)
            }
          }
        }

        def fileEventHandler(eventName: String, relativeFileName: String, baseDirPath: Path) {
          // When files are deleted from disk, the triggered "rename" event would have a relativefileName of "()"
          val filePath = typeof relativeFileName != "String"
            ? ()
            : toPath(relativeFileName, baseDirPath, createGetCanonicalFileName(sys.useCaseSensitiveFileNames))
          // Some applications save a working file via rename operations
          if ((eventName == "change" || eventName == "rename") && fileWatcherCallbacks.contains(filePath)) {
            for (val fileCallback of fileWatcherCallbacks.get(filePath)) {
              fileCallback(filePath)
            }
          }
        }
      }

      // REVIEW: for now this implementation uses polling.
      // The advantage of polling is that it works reliably
      // on all os and with network mounted files.
      // For 90 referenced files, the average time to detect
      // changes is 2*msInterval (by default 5 seconds).
      // The overhead of this is .04 percent (1/2500) with
      // average pause of < 1 millisecond (and max
      // pause less than 1.5 milliseconds); question is
      // do we anticipate reference sets in the 100s and
      // do we care about waiting 10-20 seconds to detect
      // changes for large reference sets? If so, do we want
      // to increase the chunk size or decrease the interval
      // time dynamically to match the large reference set?
      val pollingWatchedFileSet = createPollingWatchedFileSet()
      val watchedFileSet = createWatchedFileSet()

      def isNode4OrLater(): Boolean {
         return parseInt(process.version.charAt(1)) >= 4
       }

      val platform: String = _os.platform()
      // win32\win64 are case insensitive platforms, MacOS (darwin) by default is also case insensitive
      val useCaseSensitiveFileNames = platform != "win32" && platform != "win64" && platform != "darwin"

      def readFile(fileName: String, encoding?: String): String {
        if (!fileExists(fileName)) {
          return ()
        }
        val buffer = _fs.readFileSync(fileName)
        var len = buffer.length
        if (len >= 2 && buffer[0] == 0xFE && buffer[1] == 0xFF) {
          // Big endian UTF-16 byte order mark detected. Since big endian is not supported by node.js,
          // flip all byte pairs and treat as little endian.
          len &= ~1
          for (var i = 0; i < len; i += 2) {
            val temp = buffer[i]
            buffer[i] = buffer[i + 1]
            buffer[i + 1] = temp
          }
          return buffer.toString("utf16le", 2)
        }
        if (len >= 2 && buffer[0] == 0xFF && buffer[1] == 0xFE) {
          // Little endian UTF-16 byte order mark detected
          return buffer.toString("utf16le", 2)
        }
        if (len >= 3 && buffer[0] == 0xEF && buffer[1] == 0xBB && buffer[2] == 0xBF) {
          // UTF-8 byte order mark detected
          return buffer.toString("utf8", 3)
        }
        // Default is UTF-8 with no byte order mark
        return buffer.toString("utf8")
      }

      def writeFile(fileName: String, data: String, writeByteOrderMark?: Boolean): Unit {
        // If a BOM is required, emit one
        if (writeByteOrderMark) {
          data = "\uFEFF" + data
        }

        var fd: Int

        try {
          fd = _fs.openSync(fileName, "w")
          _fs.writeSync(fd, data, (), "utf8")
        }
        finally {
          if (fd != ()) {
            _fs.closeSync(fd)
          }
        }
      }

      def getCanonicalPath(path: String): String {
        return useCaseSensitiveFileNames ? path : path.toLowerCase()
      }

      val enum FileSystemEntryKind {
        File,
        Directory
      }

      def fileSystemEntryExists(path: String, entryKind: FileSystemEntryKind): Boolean {
        try {
          val stat = _fs.statSync(path)
          switch (entryKind) {
            case FileSystemEntryKind.File: return stat.isFile()
            case FileSystemEntryKind.Directory: return stat.isDirectory()
          }
        }
        catch (e) {
          return false
        }
      }

      def fileExists(path: String): Boolean {
        return fileSystemEntryExists(path, FileSystemEntryKind.File)
      }

      def directoryExists(path: String): Boolean {
        return fileSystemEntryExists(path, FileSystemEntryKind.Directory)
      }

      def readDirectory(path: String, extension?: String, exclude?: String[]): String[] {
        val result: String[] = []
        exclude = map(exclude, s => getCanonicalPath(combinePaths(path, s)))
        visitDirectory(path)
        return result
        def visitDirectory(path: String) {
          val files = _fs.readdirSync(path || ".").sort()
          val directories: String[] = []
          for (val current of files) {
            val name = combinePaths(path, current)
            if (!contains(exclude, getCanonicalPath(name))) {
              val stat = _fs.statSync(name)
              if (stat.isFile()) {
                if (!extension || fileExtensionIs(name, extension)) {
                  result.push(name)
                }
              }
              else if (stat.isDirectory()) {
                directories.push(name)
              }
            }
          }
          for (val current of directories) {
            visitDirectory(current)
          }
        }
      }

      return {
        args: process.argv.slice(2),
        newLine: _os.EOL,
        useCaseSensitiveFileNames: useCaseSensitiveFileNames,
        write(s: String): Unit {
          process.stdout.write(s)
        },
        readFile,
        writeFile,
        watchFile: (filePath, callback) => {
          // Node 4.0 stabilized the `fs.watch` def on Windows which avoids polling
          // and is more efficient than `fs.watchFile` (ref: https://github.com/nodejs/node/pull/2649
          // and https://github.com/Microsoft/TypeScript/issues/4643), therefore
          // if the current node.js version is newer than 4, use `fs.watch` instead.
          val watchSet = isNode4OrLater() ? watchedFileSet : pollingWatchedFileSet
          val watchedFile =  watchSet.addFile(filePath, callback)
          return {
            close: () => watchSet.removeFile(watchedFile)
          }
        },
        watchDirectory: (path, callback, recursive) => {
          // Node 4.0 `fs.watch` def supports the "recursive" option on both OSX and Windows
          // (ref: https://github.com/nodejs/node/pull/2649 and https://github.com/Microsoft/TypeScript/issues/4643)
          var options: any
          if (isNode4OrLater() && (process.platform == "win32" || process.platform == "darwin")) {
            options = { persistent: true, recursive: !!recursive }
          }
          else {
            options = { persistent: true }
          }

          return _fs.watch(
            path,
            options,
            (eventName: String, relativeFileName: String) => {
              // In watchDirectory we only care about adding and removing files (when event name is
              // "rename"); changes made within files are handled by corresponding fileWatchers (when
              // event name is "change")
              if (eventName == "rename") {
                // When deleting a file, the passed baseFileName is null
                callback(!relativeFileName ? relativeFileName : normalizePath(combinePaths(path, relativeFileName)))
              }
            }
          )
        },
        resolvePath: def (path: String): String {
          return _path.resolve(path)
        },
        fileExists,
        directoryExists,
        createDirectory(directoryName: String) {
          if (!this.directoryExists(directoryName)) {
            _fs.mkdirSync(directoryName)
          }
        },
        getExecutingFilePath() {
          return __filename
        },
        getCurrentDirectory() {
          return process.cwd()
        },
        readDirectory,
        getMemoryUsage() {
          if (global.gc) {
            global.gc()
          }
          return process.memoryUsage().heapUsed
        },
        exit(exitCode?: Int): Unit {
          process.exit(exitCode)
        }
      }
    }

    def getChakraSystem(): System {

      return {
        newLine: ChakraHost.newLine || "\r\n",
        args: ChakraHost.args,
        useCaseSensitiveFileNames: !!ChakraHost.useCaseSensitiveFileNames,
        write: ChakraHost.echo,
        readFile(path: String, encoding?: String) {
          // encoding is automatically handled by the implementation in ChakraHost
          return ChakraHost.readFile(path)
        },
        writeFile(path: String, data: String, writeByteOrderMark?: Boolean) {
          // If a BOM is required, emit one
          if (writeByteOrderMark) {
            data = "\uFEFF" + data
          }

          ChakraHost.writeFile(path, data)
        },
        resolvePath: ChakraHost.resolvePath,
        fileExists: ChakraHost.fileExists,
        directoryExists: ChakraHost.directoryExists,
        createDirectory: ChakraHost.createDirectory,
        getExecutingFilePath: () => ChakraHost.executingFile,
        getCurrentDirectory: () => ChakraHost.currentDirectory,
        readDirectory: ChakraHost.readDirectory,
        exit: ChakraHost.quit,
      }
    }

    if (typeof WScript != "()" && typeof ActiveXObject == "def") {
      return getWScriptSystem()
    }
    else if (typeof process != "()" && process.nextTick && !process.browser && typeof require != "()") {
      // process and process.nextTick checks if current environment is node-like
      // process.browser check excludes webpack and browserify
      return getNodeSystem()
    }
    else if (typeof ChakraHost != "()") {
      return getChakraSystem()
    }
    else {
      return (); // Unsupported host
    }
  })()
}
