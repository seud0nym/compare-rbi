package io.github.seud0nym.tch_version_compare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Firmware {
	private static final String INFO = "/usr/lib/opkg/info".replace('/', File.separatorChar);

	private final String board;
	private final String version;
	private final String basePath;
	private final Map<String, FileAttributes> files;
	private final Map<String, PackageAttributes> opkgs;

	@SuppressWarnings("unchecked")
	public Firmware(final File directory, final boolean useCache) throws IOException, NoSuchAlgorithmException {
		basePath = directory.getAbsolutePath();

		if (!directory.isDirectory()) {
			throw new IOException(basePath
					+ " is not a directory. You must specify the top-level directory of the converted firmware file");
		}

		Pattern boardPattern = Pattern.compile("(V.NT-.)", Pattern.CASE_INSENSITIVE);
		Matcher boardMatcher = boardPattern.matcher(basePath);
		String boardPrefix = null;
		if (boardMatcher.find()) {
			board = boardMatcher.group(1).toUpperCase();
			boardPrefix = "/etc/boards/" + board + "/config/";
		} else {
			throw new IOException("Unable to extract board from directory name. ");
		}

		StringBuffer versionBuffer = new StringBuffer();
		try (Stream<String> lines = Files.lines(Paths.get(basePath, "etc", "config", "version"))) {
			Optional<String> line = lines.filter(l -> l.contains("option version")).findFirst();
			if (line.isPresent()) {
				Pattern versionPattern = Pattern.compile("option version '(.*?)-");
				Matcher versionMatcher = versionPattern.matcher(line.get());
				if (versionMatcher.find()) {
					versionBuffer.append(versionMatcher.group(1));
				} else {
					throw new IOException("Unable to extract version number");
				}
			}
		}
		Path versioncusto = Paths.get(basePath, "etc", "config", "versioncusto");
		if (!versioncusto.toFile().exists()) {
			versioncusto = Paths.get(basePath, "etc", "boards", board, "config", "etc", "config", "versioncusto");
		}
		try (Stream<String> lines = Files.lines(versioncusto)) {
			Optional<String> line = lines.filter(l -> l.contains("option fwversion_suffix")).findFirst();
			if (line.isPresent()) {
				Pattern versionPattern = Pattern.compile("option fwversion_suffix '(.*)'");
				Matcher versionMatcher = versionPattern.matcher(line.get());
				if (versionMatcher.find()) {
					versionBuffer.append(versionMatcher.group(1));
				} else {
					throw new IOException("Unable to extract version number suffix");
				}
			}
		}
		version = versionBuffer.toString();

		File fileCache = new File(System.getProperty("java.io.tmpdir"),
				URLEncoder.encode(basePath + "-files", "UTF-8"));
		File opkgCache = new File(System.getProperty("java.io.tmpdir"),
				URLEncoder.encode(basePath + "-opkgs", "UTF-8"));

		Map<String, FileAttributes> filesMap = null;
		Map<String, PackageAttributes> opkgsMap = null;
		if (useCache && fileCache.exists() && opkgCache.exists()) {
			filesMap = (Map<String, FileAttributes>) loadFromCache(fileCache);
			opkgsMap = (Map<String, PackageAttributes>) loadFromCache(opkgCache);
		}

		if (filesMap == null || opkgsMap == null) {
			files = new TreeMap<>();
			opkgs = new TreeMap<>();
			recurse(directory);

			for (PackageAttributes opkg : opkgs.values()) {
				String pkg = opkg.getPackageName();
				for (String file : opkg.getFiles()) {
					if (file.equals("/init") || file.equals("/www/api") || file.equals("/www/themes")) {
						continue;
					}
					FileAttributes attributes = files.get(file);
					if (attributes == null) {
						if (file.startsWith("/etc/custo/")) {
							attributes = files.get(file.replace("/custo/", "/"));
						} else if (file.startsWith("/etc/boards/")) {
							if (file.startsWith(board)) {
								if (file.startsWith(boardPrefix)) {
									attributes = files.get(file.substring(board.length()));
								}
							} else {
								continue;
							}
						} else {
							Optional<FileAttributes> optAttributes = files.entrySet().stream()
									.filter(e -> e.getKey().equalsIgnoreCase(file)).map(Map.Entry::getValue)
									.findFirst();
							if (optAttributes.isPresent()) {
								attributes = optAttributes.get();
							} else {
								System.err.println("File " + file + " from package " + pkg + " not found!");
							}
						}
					}
					if (attributes != null) {
						attributes.setPackage(pkg);
					}
				}
			}

			persist(fileCache, files);
			persist(opkgCache, opkgs);
		} else {
			files = filesMap;
			opkgs = opkgsMap;
		}
	}

	public void compareWith(Firmware fw, boolean includePackageSizeChanged) {
		if (!this.getBoard().equals(fw.getBoard())) {
			throw new RuntimeException("Cannot compare board " + this.getBoard() + " to " + fw.getBoard());
		}

		final int c = this.getVersion().compareTo(fw.getVersion());
		final Firmware current;
		final Firmware previous;

		if (c == 0) {
			throw new RuntimeException(
					"Firmware versions are identical! (" + this.getVersion() + " == " + fw.getVersion() + ")");
		} else if (c < 0) {
			current = fw;
			previous = this;
		} else {
			current = this;
			previous = fw;
		}

		System.out.printf("Looking for changes between %s %s and %s (Package installed size changes are %s)...\n",
				previous.getBoard(), previous.getVersion(), current.getVersion(),
				includePackageSizeChanged ? "included" : "excluded");

		final String descfmt = "           %s\n";

		for (Map.Entry<String,PackageAttributes> e : current.opkgs.entrySet()) {
			PackageAttributes crntAttr = e.getValue();
			PackageAttributes prevAttr = previous.opkgs.get(e.getKey());
			if (prevAttr == null) {
				System.out.printf("+ ADDED   Package %s %s\n", crntAttr.getPackageName(), crntAttr.getVersion());
				crntAttr.getDescription().forEach(d -> System.out.printf(descfmt, d));
			} else if (!crntAttr.getVersion().equals(prevAttr.getVersion())) {
				System.out.printf("~ CHANGED Package %s %s to Version %s\n", crntAttr.getPackageName(),
						prevAttr.getVersion(), crntAttr.getVersion());
				crntAttr.getDescription().forEach(d -> System.out.printf(descfmt, d));
			} else if (includePackageSizeChanged && crntAttr.getInstalledSize() != prevAttr.getInstalledSize()) {
				System.out.printf("~ CHANGED Package %s %s (Note: Version unchanged, but Installed Size changed from %d to %d)\n", crntAttr.getPackageName(),
						crntAttr.getVersion(), prevAttr.getInstalledSize(), crntAttr.getInstalledSize());
				crntAttr.getDescription().forEach(d -> System.out.printf(descfmt, d));
			}
		}
		for (String p : previous.opkgs.keySet()) {
			if (current.opkgs.get(p) == null) {
				PackageAttributes prevAttr = previous.opkgs.get(p);
				System.out.printf("- REMOVED Package %s %s\n", prevAttr.getPackageName(), prevAttr.getVersion());
				prevAttr.getDescription().forEach(d -> System.out.printf(descfmt, d));
			}
		}
		for (Map.Entry<String,FileAttributes> e : current.files.entrySet().stream().filter(e -> e.getValue().getPackage() == null)
				.collect(Collectors.toList())) {
			String name = e.getKey();
			FileAttributes crntAttr = e.getValue();
			if (crntAttr.isDirectory() || name.startsWith("/usr/lib/opkg/") || name.startsWith("/etc/config/version")) {
				continue;
			}
			FileAttributes prevAttr = previous.files.get(name);
			if (prevAttr == null) {
				System.out.printf("+ ADDED   Unpackaged File %s\n", name);
			} else {
				if (crntAttr.isSymbolicLink() && prevAttr.isSymbolicLink()) {
					if (!crntAttr.getTarget().equals(prevAttr.getTarget())) {
						System.out.printf("~ CHANGED Unpackaged Symbolic Link %s\n", name);
					}
				} else {
					String crntDigest = crntAttr.getDigest();
					if (crntDigest == null) {
						System.err.println("DIGEST is NULL for " + name);
					} else {
						String prevDigest = prevAttr.getDigest();
						if (!crntDigest.equals(prevDigest)) {
							System.out.printf("~ CHANGED Unpackaged File %s\n", name);
						}
					}
				}
			}
		}
		for (String f : previous.files.entrySet().stream().filter(e -> e.getValue().getPackage() == null)
				.map(Map.Entry::getKey).collect(Collectors.toList())) {
			if (current.files.get(f) == null) {
				if (f.startsWith("/etc/boards/") && f.contains(board)) {
					System.out.printf("- REMOVED Unpackaged File %s\n", f);
				}
			}
		}
	}

	public String getBoard() {
		return board;
	}

	public int getFileCount() {
		return files.size();
	}

	public String getVersion() {
		return version;
	}

	private void recurse(File directory) throws NoSuchAlgorithmException, IOException {
		boolean opkg = directory.getAbsolutePath().endsWith(INFO);
		for (final File file : directory.listFiles()) {
			if (file.isDirectory()) {
				files.put(toFilename(file), new FileAttributes(file));
				recurse(file);
			} else {
				files.put(toFilename(file), new FileAttributes(file));
				String name = file.getName();
				if (opkg && name.endsWith(".control")) {
					String pkg = name.substring(0, name.length() - 8);
					opkgs.put(pkg, new PackageAttributes(file, new File(directory, pkg + ".list")));
				}
			}
		}
	}

	private String toFilename(final File file) {
		char[] name = file.getAbsolutePath().toCharArray();
		StringBuffer result = new StringBuffer();
		for (int c = basePath.length(); c < name.length; c++) {
			// System.out.println((int)name[c]);
			switch (name[c]) {
				case '\\':
					result.append('/');
					break;
				case 61498:
					result.append(':');
					break;
				default:
					result.append(name[c]);
					break;
			}
		}
		return result.toString();
	}

	private static void persist(File cache, Object o) throws IOException, FileNotFoundException {
		try (FileOutputStream fos = new FileOutputStream(cache)) {
			try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
				oos.writeObject(o);
			}
		}
	}

	private static Object loadFromCache(File file) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
			try (ObjectInputStream ois = new ObjectInputStream(fis)) {
				return ois.readObject();
			} catch (Exception e) {
				return null;
			}
		}
	}
}
