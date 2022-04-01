# compare-rbi

Compares Technicolor firmware extracted from rbi files and shows the differences in the standard packages and any unpackaged files.

## Usage

Decrypt the two firmware rbi files to be compared using https://github.com/seud0nym/decrypt-rbi (or if you prefer a GUI interface, use https://github.com/Ansuel/Decrypt_RBI_Firmware_Utility), and then extract their contents into 2 separate directories.

For example:
```
java -jar decrypt-rbi.jar firmware_1.rbi
java -jar decrypt-rbi.jar firmware_2.rbi
```

The resulting files (in this example `firmware_1.bin` and `firmware_2.bin`) are squashfs file systems. You can extract the contents from these files by using a utility such as [p7zip](https://www.7-zip.org/). 

For example:
```
/usr/bin/7z x -o'./firmware_1_extract' 'firmware_1.bin'
/usr/bin/7z x -o'./firmware_2_extract' 'firmware_2.bin'
```

This will create the `firmware_1_extract` and `firmware_2_extract` directories containing the firmware contents.

**NOTE** It is *not* recommended to extract the directories on a Windows system, as it requires a file system that supports *case-sensitive* file names. 

Download the latest compare-rbi.jar release file and then run it by passing the paths to the two extracted directories. For example:
```
java -jar compare-rbi.jar ./firmware_1_extract ./firmware_2_extract
```

This will compare the directories and report differences in the system installed packages and any unpackaged files.

### Optional Parameters

#### -incpkgsizechanged

This will include packages where the version number has not changed, but the size of the package is different.

For example:
```
java -jar compare-rbi.jar -incpkgsizechanged ./firmware_1_extract ./firmware_2_extract
```

#### -usecache

All files in the firmware extract must be read and their checksums computed, which is a time-consuming operation. If you are comparing the same firmware extract with other extracts, you can re-use the temporary cache to speed up processing. Otherwise, the cache will be rebuilt each time.

For example:
```
java -jar compare-rbi.jar -usecache ./firmware_1_extract ./firmware_3_extract
```
