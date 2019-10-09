package com.twinsoft.convertigo.engine;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.beans.references.ProjectSchemaReference;
import com.twinsoft.convertigo.engine.util.GitUtils;
import com.twinsoft.convertigo.engine.util.ProjectUrlParser;

public class ReferencedProjectManager {


	public boolean check() {
		List<String> names = Engine.theApp.databaseObjectsManager.getAllProjectNamesList();
		boolean loaded = check(names);
		if (loaded) {
			Engine.theApp.fireMigrationFinished(new EngineEvent(""));
		}
		return loaded;
	}
	
	private boolean check(List<String> names) {
		Map<String, ProjectUrlParser> refs = new HashMap<>();
		for (String name: names) {
			try {
				Project project = Engine.theApp.databaseObjectsManager.getOriginalProjectByName(name, true);
				project.getReferenceList().forEach(r -> {
					if (r instanceof ProjectSchemaReference) {
						ProjectSchemaReference ref = (ProjectSchemaReference) r;
						String url = ref.getProjectName();
						ProjectUrlParser parser = new ProjectUrlParser(url);
						if (parser.isValid()) {
							refs.put(parser.getProjectName(), parser);
						}
					}
				});
			} catch (Exception e) {
				Engine.logEngine.error("Failed to load " + name, e);
			}
		}
		List<String> loaded = new LinkedList<>();
		for (Entry<String, ProjectUrlParser> entry: refs.entrySet()) {
			String projectName = entry.getKey();
			try {
				if (importProject(entry.getValue())) {
					loaded.add(projectName);
				}
			} catch (Exception e) {
				Engine.logEngine.warn("(ReferencedProjectManager) Failed to load '" + projectName + "'", e);
			}
		}
		if (!loaded.isEmpty()) {
			check(loaded);
			return true;
		}
		return false;
	}
	
	public boolean importProject(ProjectUrlParser parser) throws Exception {
		String projectName = parser.getProjectName();
		Project project = Engine.theApp.databaseObjectsManager.getOriginalProjectByName(projectName, false);
		File dir = null;
		File prjDir = null;
		if (project != null) {
			prjDir = project.getDirFile();
			dir = GitUtils.getWorkingDir(project.getDirFile());
			if (dir != null) {
				Engine.logEngine.info("(ReferencedProjectManager) " + projectName + " has repo " + dir);
			} else {
				Engine.logEngine.info("(ReferencedProjectManager) " + projectName + " exists without repo");
			}
		} else {
			File gitContainer = GitUtils.getGitContainer();
			dir = new File(gitContainer, parser.getGitRepo());
			if (dir.exists()) {
				if (GitUtils.asRemoteAndBranch(dir, parser.getGitUrl(), parser.getGitBranch())) {
					Engine.logEngine.info("(ReferencedProjectManager) folder has remote " + parser.getGitUrl());
				} else {
					Engine.logEngine.info("(ReferencedProjectManager) folder hasn't remote " + parser.getGitUrl());
					int i = 1;
					while (i > 0 && (dir = new File(gitContainer, parser.getGitRepo() + "_" + i++)).exists()) {
						if (GitUtils.asRemoteAndBranch(dir, parser.getGitUrl(), parser.getGitBranch())) {
							i = 0;
						}
					}
					Engine.logEngine.info("(ReferencedProjectManager) new folder " + dir);
				}
			}
			if (!dir.exists()) {
				GitUtils.clone(parser.getGitUrl(), parser.getGitBranch(), dir);
			} else {
				Engine.logEngine.info("(ReferencedProjectManager) Use repo " + dir);
			}
			if (parser.getProjectPath() != null) {
				prjDir = new File(dir, parser.getProjectPath());
			} else {
				prjDir = dir;
			}
		}
		if (dir != null) {
			//GitUtils.pull(dir);
			if (project == null) {
				Project prj = Engine.theApp.databaseObjectsManager.importProject(new File(prjDir, "c8oProject.yaml"));
				Engine.logEngine.info("(ReferencedProjectManager) Referenced project is loaded: " + prj);
				return true;
			}
		}
		return false;
	}
}