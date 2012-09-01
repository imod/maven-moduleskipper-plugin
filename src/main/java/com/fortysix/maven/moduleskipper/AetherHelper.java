package com.fortysix.maven.moduleskipper;

import org.apache.maven.repository.internal.DefaultServiceLocator;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.MavenServiceLocator;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;

public class AetherHelper {

	public static RepositorySystem getRepositorySystem() {
		final MavenServiceLocator locator = new MavenServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class);
		locator.addService(RepositoryConnectorFactory.class, AsyncRepositoryConnectorFactory.class);
		return locator.getService(RepositorySystem.class);
	}

	public static MavenRepositorySystemSession getRepoSession(RepositorySystem system, String localRepo) {
		final MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		final LocalRepository local = new LocalRepository(localRepo);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(local));
		return session;
	}
}
