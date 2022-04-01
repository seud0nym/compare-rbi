package io.github.seud0nym.tch_version_compare;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class PackageAttributes implements Serializable {
	private static final long serialVersionUID = 420395486904922706L;

	private final String name;
	private final String version;
	private final String depends;
	private final String source;
	private final String license;
	private final String section;
	private final String maintainer;
	private final String architecture;
	private final long installedSize;
	private final List<String> description;
	private final List<String> files;

	public PackageAttributes(final File control, final File list) throws IOException, NoSuchAlgorithmException {
		if (!control.isFile()) {
			throw new IOException(control.getAbsolutePath() + " is not a file!");
		}
		if (!list.isFile()) {
			throw new IOException(list.getAbsolutePath() + " is not a file!");
		}

		String name = "";
		String version = "";
		String depends = "";
		String source = "";
		String license = "";
		String section = "";
		String maintainer = "";
		String architecture = "";
		long installedSize = 0L;
		boolean inDescription = false;

		description = new ArrayList<>();
	
		for (String line : Files.readAllLines(control.toPath())) {
			if (inDescription) {
				String stripped = strip(line);
				if (stripped.length() > 0) {
					description.add(stripped);
				}
			} else {
				String[] s = line.split(":");
				switch (s[0]) {
					case "Package": name = s.length == 2 ? strip(s[1]) : ""; break;
					case "Version": version = s.length == 2 ? strip(s[1]) : ""; break;
					case "Depends": depends = s.length == 2 ? strip(s[1]) : ""; break;
					case "Source": source = s.length == 2 ? strip(s[1]) : ""; break;
					case "License": license = s.length == 2 ? strip(s[1]) : ""; break;
					case "Section": section = s.length == 2 ? strip(s[1]) : ""; break;
					case "Maintainer": maintainer = s.length == 2 ? strip(s[1]) : ""; break;
					case "Architecture": architecture = s.length == 2 ? strip(s[1]) : ""; break;
					case "Installed-Size": installedSize = s.length == 2 ? Long.parseLong(strip(s[1])) : 0L; break;
					case "Description": if (s.length == 2) { description.add(strip(s[1])); inDescription = true; }; break;
				}
			}
		}

		this.name = name;
		this.version = version;
		this.depends = depends;
		this.source = source;
		this.license = license;
		this.section = section;
		this.maintainer = maintainer;
		this.architecture = architecture;
		this.installedSize = installedSize;
		this.files = Files.readAllLines(list.toPath());
	}

	public String getPackageName() {
		return name;
	}

	public String getVersion() {
		return version;
	}

	public String getDepends() {
		return depends;
	}

	public String getSource() {
		return source;
	}

	public String getLicense() {
		return license;
	}

	public String getSection() {
		return section;
	}

	public String getMaintainer() {
		return maintainer;
	}

	public String getArchitecture() {
		return architecture;
	}

	public long getInstalledSize() {
		return installedSize;
	}

	public List<String> getDescription() {
		return description;
	}

	public List<String> getFiles() {
		return files;
	}

	private static String strip(String str) {
		return str.replaceAll("^[ \t]+|[ \t]+$", "");
	}
}
