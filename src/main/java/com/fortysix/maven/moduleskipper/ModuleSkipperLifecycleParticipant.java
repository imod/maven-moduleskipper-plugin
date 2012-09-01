/**
 * 
 */
package com.fortysix.maven.moduleskipper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * Maven Extension which is able to remove a project of the current
 * reactor/build session if the same version is already deployed to a remote
 * repository. <br>
 * This is most useful if you have a multi module project where some artifacts
 * have to be released with different classifiers.
 * <p>
 * How to write an extension: <a href=
 * "http://brettporter.wordpress.com/2010/10/05/creating-a-custom-build-extension-for-maven-3-0/"
 * >custom-build-extension-for-maven</a>
 * 
 * @author domi
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "moduleskipper")
public class ModuleSkipperLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	private static final Map<String, String> PACKAGING_2_PLUGIN = new HashMap<String, String>();
	private static final Map<String, String> PACKAGING_2_EXTENSION = new HashMap<String, String>();
	static {
		PACKAGING_2_PLUGIN.put("jar", "org.apache.maven.plugins:maven-jar-plugin");
		PACKAGING_2_PLUGIN.put("war", "org.apache.maven.plugins:maven-war-plugin");
		PACKAGING_2_PLUGIN.put("ear", "org.apache.maven.plugins:maven-ear-plugin");
		PACKAGING_2_PLUGIN.put("ejb", "org.apache.maven.plugins:maven-ejb-plugin");
		PACKAGING_2_PLUGIN.put("rar", "org.apache.maven.plugins:maven-rar-plugin");

		PACKAGING_2_EXTENSION.put("jar", "jar");
		PACKAGING_2_EXTENSION.put("war", "war");
		PACKAGING_2_EXTENSION.put("ear", "ear");
		PACKAGING_2_EXTENSION.put("ejb", "jar");
		PACKAGING_2_EXTENSION.put("rar", "rar");
		PACKAGING_2_EXTENSION.put("pom", "pom");
	}

	@Requirement
	private Logger logger;

	private boolean ignoreSnapshots = true;

	private static Set<String> DEFAULT_TRIGGER_GOALS = new HashSet<String>();
	static {
//		DEFAULT_TRIGGER_GOALS.add("install");
		DEFAULT_TRIGGER_GOALS.add("deploy");
	}

	@Override
	public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
		super.afterProjectsRead(session);

		boolean skip = Boolean.getBoolean("moduleskipper.skip");
		if (!skip) {

			List<String> goals = session.getRequest().getGoals();// session.getGoals();
			System.out.println(">>>>" + goals);
			if (containsAny(DEFAULT_TRIGGER_GOALS, goals)) {

				final String repo = session.getLocalRepository().getBasedir();
				List<MavenProject> projectsToRemove = new ArrayList<MavenProject>();

				List<MavenProject> projects = session.getProjects();
				for (MavenProject mavenProject : projects) {

					logger.info("project: " + mavenProject);

					if (isRelease(mavenProject.getVersion()) || ignoreSnapshots) {

						String classifier = lookupClassifier(mavenProject);
						String packaging = mavenProject.getPackaging();
						String extension = PACKAGING_2_EXTENSION.get(packaging) == null ? packaging : PACKAGING_2_EXTENSION.get(packaging); // fallback
																																			// to
																																			// packaging
																																			// as
																																			// extension

						logger.info("Classifier: " + classifier);

						ArtifactRequest request = new ArtifactRequest();
						request.setArtifact(new DefaultArtifact(mavenProject.getGroupId(), mavenProject.getArtifactId(), classifier, extension, mavenProject.getVersion()));
						request.setRepositories(mavenProject.getRemoteProjectRepositories());

						ArtifactResult result;
						try {
							logger.debug("Search for: " + request);

							RepositorySystem repositorySystem = AetherHelper.getRepositorySystem();
							result = repositorySystem.resolveArtifact(AetherHelper.getRepoSession(repositorySystem, repo), request);

							if (result != null && result.isResolved()) {
								projectsToRemove.add(mavenProject);
							}

							logger.debug("Resolved artifact " + request + " to " + result.getArtifact().getFile() + " from " + result.getRepository());
						} catch (ArtifactResolutionException e) {
							System.err.println(e.getMessage());
						}
					}
				}
				for (MavenProject mavenProject : projectsToRemove) {
					logger.warn("remove [" + mavenProject + "] from reactor!");
					projects.remove(mavenProject);
				}

				if (projects.isEmpty()) {
					throw new MavenExecutionException("There are no projects left in the reactor, ModuleSkipper removed all projects - can't run a build!", new IllegalStateException());
				}
			}
		}
	}

	/**
	 * Checks if any string of <code>targetSet</code> is contained in
	 * <code>originSet</code>
	 * 
	 * @param originSet
	 *            origin set
	 * @param targetSet
	 *            set to be checked if it contains any string from the origin
	 *            set
	 * @return <code>true</code> if target contains anything from origin;
	 */
	private boolean containsAny(Collection<String> originSet, Collection<String> targetSet) {
		for (String goal : targetSet) {
			if (originSet.contains(goal)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Extracts the classifier of the correct plugin for the given project
	 * 
	 * @param project
	 *            to extract the classifier depending on the packaging of the
	 *            passed project
	 * @return might be empty, but nerver <code>null</code>
	 */
	private String lookupClassifier(MavenProject project) {

		String pluginKey = PACKAGING_2_PLUGIN.get(project.getPackaging());

		List<Plugin> plugins = project.getBuildPlugins();

		for (Iterator<Plugin> iterator = plugins.iterator(); iterator.hasNext();) {
			Plugin plugin = iterator.next();
			if (pluginKey.equalsIgnoreCase(plugin.getKey())) {
				return getClassifier(plugin);
			}
		}
		return null;
	}

	/**
	 * extracts the classifier of the plugins configurtion
	 * 
	 * @param plugin
	 *            the plugin to pull the classifier of its configuration
	 * @return might be empty, but never <code>null</code>
	 */
	private String getClassifier(Plugin plugin) {
		Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
		if (configuration != null) {
			Xpp3Dom classifier = configuration.getChild("classifier");
			return classifier.getValue();
		}
		return "";
	}

	private boolean isSnapshot(String version) {
		return version.contains("SNAPSHOT");
	}

	private boolean isRelease(String version) {
		return !isSnapshot(version);
	}
}
