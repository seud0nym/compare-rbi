package io.github.seud0nym.tch_version_compare;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileAttributes implements Serializable {
	private static final long serialVersionUID = 6637108341666416246L;
	private static final String HEXES = "0123456789ABCDEF";

	private final boolean directory;
	private final boolean link;
	private final long size;
	private final String digest;
	private final String target;
	private String opkg;

	public FileAttributes(final File file) throws IOException, NoSuchAlgorithmException {
		Path path = file.toPath(); 
		directory = file.isDirectory();
		link = Files.isSymbolicLink(path);
		
		if (directory) {
			digest = null;
			size = 0L;
			target = null;
		} else if (link) {
			digest = null;
			size = 0L;
			target = Files.readSymbolicLink(path).toString();
		} else {
			final MessageDigest complete = MessageDigest.getInstance("SHA1");
			try (FileInputStream fis =  new FileInputStream(file)) {
				byte[] buffer = new byte[1024];
				int numRead;
				do {
					numRead = fis.read(buffer);
					if (numRead > 0) {
						complete.update(buffer, 0, numRead);
					}
				} while (numRead != -1);
			}
			final byte[] raw = complete.digest();

			final StringBuilder hex = new StringBuilder( 2 * raw.length );
			for (final byte b : raw ) {
				hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
			}

			digest = hex.toString();
			size = file.length();
			target = null;
		}
	}

	public long getSize() {
		return size;
	}

	public String getDigest() {
		return digest;
	}

	public String getPackage() {
		return opkg;
	}

	public String getTarget() {
		return target;
	}

	public void setPackage(final String opkg) {
		this.opkg = opkg;
	}

	public boolean isDirectory() {
		return directory;
	}

	public boolean isSymbolicLink() {
		return link;
	}
}
