/*
 * Copyright (c) 2001-2018 Convertigo SA.
 * 
 * This program  is free software; you  can redistribute it and/or
 * Modify  it  under the  terms of the  GNU  Affero General Public
 * License  as published by  the Free Software Foundation;  either
 * version  3  of  the  License,  or  (at your option)  any  later
 * version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY;  without even the implied warranty of
 * MERCHANTABILITY  or  FITNESS  FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program;
 * if not, see <http://www.gnu.org/licenses/>.
 */

package com.twinsoft.convertigo.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.EventListenerList;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.twinsoft.convertigo.beans.BeansDefaultValues;
import com.twinsoft.convertigo.beans.common.XMLVector;
import com.twinsoft.convertigo.beans.core.Connector;
import com.twinsoft.convertigo.beans.core.DatabaseObject;
import com.twinsoft.convertigo.beans.core.IStepSourceContainer;
import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.beans.core.RequestableStep;
import com.twinsoft.convertigo.beans.core.Sequence;
import com.twinsoft.convertigo.beans.core.Step;
import com.twinsoft.convertigo.beans.core.StepWithExpressions;
import com.twinsoft.convertigo.beans.core.Transaction;
import com.twinsoft.convertigo.beans.steps.ReadFileStep;
import com.twinsoft.convertigo.beans.steps.SmartType;
import com.twinsoft.convertigo.beans.steps.TransactionStep;
import com.twinsoft.convertigo.beans.steps.XMLActionStep;
import com.twinsoft.convertigo.beans.steps.XMLGenerateDatesStep;
import com.twinsoft.convertigo.beans.variables.StepVariable;
import com.twinsoft.convertigo.engine.EnginePropertiesManager.PropertyName;
import com.twinsoft.convertigo.engine.dbo_explorer.DboBean;
import com.twinsoft.convertigo.engine.dbo_explorer.DboBeans;
import com.twinsoft.convertigo.engine.dbo_explorer.DboCategory;
import com.twinsoft.convertigo.engine.dbo_explorer.DboExplorerManager;
import com.twinsoft.convertigo.engine.dbo_explorer.DboGroup;
import com.twinsoft.convertigo.engine.dbo_explorer.DboParent;
import com.twinsoft.convertigo.engine.dbo_explorer.DboUtils;
import com.twinsoft.convertigo.engine.enums.DeleteProjectOption;
import com.twinsoft.convertigo.engine.helpers.WalkHelper;
import com.twinsoft.convertigo.engine.migration.Migration001;
import com.twinsoft.convertigo.engine.migration.Migration3_0_0;
import com.twinsoft.convertigo.engine.migration.Migration5_0_0;
import com.twinsoft.convertigo.engine.migration.Migration5_0_4;
import com.twinsoft.convertigo.engine.migration.Migration7_0_0;
import com.twinsoft.convertigo.engine.migration.Migration7_4_0;
import com.twinsoft.convertigo.engine.mobile.MobileBuilder;
import com.twinsoft.convertigo.engine.providers.couchdb.CouchDbManager;
import com.twinsoft.convertigo.engine.util.CarUtils;
import com.twinsoft.convertigo.engine.util.FileUtils;
import com.twinsoft.convertigo.engine.util.GenericUtils;
import com.twinsoft.convertigo.engine.util.ProjectUtils;
import com.twinsoft.convertigo.engine.util.PropertiesUtils;
import com.twinsoft.convertigo.engine.util.StringUtils;
import com.twinsoft.convertigo.engine.util.VersionUtils;
import com.twinsoft.convertigo.engine.util.XMLUtils;
import com.twinsoft.convertigo.engine.util.YamlConverter;
import com.twinsoft.convertigo.engine.util.ZipUtils;

/**
 * This class is responsible for serializing objects to the Convertigo database
 * repository and restoring them from the Convertigo database repository.
 */
public class DatabaseObjectsManager implements AbstractManager {
	private static final Pattern pValidSymbolName = Pattern.compile("[\\{=}\\r\\n]");
	private static final Pattern pFindSymbol = Pattern.compile("\\$\\{([^\\{\\r\\n]*?)(?:=(.*?(?<!\\\\)))?}");
	private static final Pattern pFindEnv = Pattern.compile("\\%([^\\r\\n]*?)(?:=(.*?(?<!\\\\)))?\\%");
	private static final Pattern pYamlProjectVersion = Pattern.compile("↑(?:(beans)|.*?): (.*)");
	private static final Pattern pYamlProjectName = Pattern.compile("(?:↑.*?:.*)|(?:↓(.*?) \\[core\\.Project\\]: )");
	private static final Pattern pProjectName = Pattern.compile("(.*)\\.(?:xml|car)");
	
	public static interface StudioProjects {
		default void declareProject(File projectXml) {
		}
		
		default boolean canOpen(String projectName) {
			return true;
		}
		
		default public Map<String, File> getProjects(boolean checkOpenable) {
			return Collections.emptyMap();
		}
		
		default public File getProject(String projectName) {
			File file = new File(Engine.PROJECTS_PATH + "/" + projectName + "/" + projectName + ".xml");
			return file.exists() ? file : null;
		}
	}
	
	public static StudioProjects studioProjects = new StudioProjects() {};
	
	private Map<String, Project> projects;
	
	private String globalSymbolsFilePath = null;
	/**
	 * The symbols repository for compiling text properties.
	 */
	private Properties symbolsProperties;

	// private static String XSL_NAMESPACE_URI =
	// "http://www.w3.org/1999/XSL/Transform";

	public DatabaseObjectsManager() {
	}

	public void init() throws EngineException {
		projects = new HashMap<String, Project>();
		symbolsInit();
	}
	
	public void destroy() throws EngineException {
		projects = null;
		symbolsProperties = null;
	}

	private EventListenerList databaseObjectListeners = new EventListenerList();

	public void addDatabaseObjectListener(DatabaseObjectListener databaseObjectListener) {
		databaseObjectListeners.add(DatabaseObjectListener.class, databaseObjectListener);
	}

	public void removeDatabaseObjectListener(DatabaseObjectListener databaseObjectListener) {
		databaseObjectListeners.remove(DatabaseObjectListener.class, databaseObjectListener);
	}

	public void fireDatabaseObjectLoaded(DatabaseObjectLoadedEvent event) {
		// Guaranteed to return a non-null array
		Object[] listeners = databaseObjectListeners.getListenerList();
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == DatabaseObjectListener.class) {
				((DatabaseObjectListener) listeners[i + 1]).databaseObjectLoaded(event);
			}
		}
	}

	public void fireDatabaseObjectImported(DatabaseObjectImportedEvent event) {
		// Guaranteed to return a non-null array
		Object[] listeners = databaseObjectListeners.getListenerList();
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == DatabaseObjectListener.class) {
				((DatabaseObjectListener) listeners[i + 1]).databaseObjectImported(event);
			}
		}
	}

	public List<String> getAllProjectNamesList() {
		return getAllProjectNamesList(true);
	}
	
	public List<String> getAllProjectNamesList(boolean checkOpenable) {		
		Engine.logDatabaseObjectManager.trace("Retrieving all project names from \"" + Engine.PROJECTS_PATH + "\"");
		
		File projectsDir = new File(Engine.PROJECTS_PATH);
		SortedSet<String> projectNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		projectNames.addAll(studioProjects.getProjects(checkOpenable).keySet());
		
		for (File projectDir : projectsDir.listFiles()) {
			String projectName = projectDir.getName();
			
			if (!projectNames.contains(projectName) && projectDir.isDirectory() && new File(projectDir, projectName + ".xml").exists()) {
				if (!checkOpenable || canOpenProject(projectName)) {
					projectNames.add(projectName);
				} else {
					clearCache(projectName);
				}
			}
		}
		
		Engine.logDatabaseObjectManager.trace("Project names found: " + projectNames.toString());
		return new ArrayList<String>(projectNames);
	}

	public String[] getAllProjectNamesArray() {
		Collection<String> c = getAllProjectNamesList();
		return c.toArray(new String[c.size()]);
	}

	protected void checkForEngineMigrationProcess(String projectName) throws ProjectInMigrationProcessException {
		if (!(Thread.currentThread() instanceof MigrationJob)) {
			if (!MigrationManager.isProjectMigrated(projectName)) {
				throw new ProjectInMigrationProcessException();
			}
		}
	}

	// Thread reference to currently loaded project for log needs
	public static class ProjectLoadingData {
		public ProjectLoadingData() {}

		public String projectName;
		public boolean undefinedGlobalSymbol = false;
	}

	private static ThreadLocal<ProjectLoadingData> projectLoadingDataThreadLocal = new ThreadLocal<ProjectLoadingData>() {
		@Override
		protected ProjectLoadingData initialValue() {
			return new ProjectLoadingData();
		}
	};

	public static ProjectLoadingData getProjectLoadingData() {
		return projectLoadingDataThreadLocal.get();
	}
	
	public Project getOriginalProjectByName(String projectName) throws EngineException {
		return getOriginalProjectByName(projectName, true);
	}
	
	public Project getOriginalProjectByName(String projectName, boolean checkOpenable) throws EngineException {
		Engine.logDatabaseObjectManager.trace("Requiring loading of project \"" + projectName + "\"");
		
		File projectPath = studioProjects.getProjects(checkOpenable).get(projectName);
		if (projectPath == null) {
			projectPath = Engine.projectYamlFile(projectName);
			if (projectPath == null || !projectPath.exists()) {
				projectPath = Engine.projectFile(projectName);
			}
		}
		
		if (checkOpenable && !canOpenProject(projectName) || projectPath == null || !projectPath.exists()) {
			Engine.logDatabaseObjectManager.trace("The project \"" + projectName + "\" cannot be open");
			clearCache(projectName);
			return null;
		}
		
		Project project;
		
		synchronized (projects) {
			project = projects.get(projectName);
		}
		
		if (project == null) {		
			long t0 = Calendar.getInstance().getTime().getTime();

			try {
				checkForEngineMigrationProcess(projectName);
				project = importProject(projectPath);
			} catch (ClassCastException e) {
				throw new EngineException("The requested object \"" + projectName + "\" is not a project!", e);
			} catch (ProjectInMigrationProcessException e) {
				throw new EngineException("Unable to load the project \"" + projectName
						+ "\": the project is in migration process.", e);
			} catch (Exception e) {
				throw new EngineException("Unable to load the project \"" + projectName + "\"", e);
			} finally {
				long t1 = Calendar.getInstance().getTime().getTime();
				Engine.logDatabaseObjectManager.trace("Project loaded in " + (t1 - t0) + " ms");
			}
		} else {
			Engine.logDatabaseObjectManager.trace("Retrieve from cache project \"" + projectName + "\"");
		}
		
		return project;
	}
	
	public static boolean checkParent(Class<? extends DatabaseObject> parentObjectClass, DboBean bean) throws ClassNotFoundException {
        Collection<DboParent> parents = bean.getParents();
        for (DboParent possibleParent : parents) {
            // Check if parent allow inheritance
            if (Class.forName(possibleParent.getClassName()).equals(parentObjectClass) ||
                possibleParent.allowInheritance() && Class.forName(possibleParent.getClassName()).isAssignableFrom(parentObjectClass)) {
                    return true;
            }
        }
        return false;
	}
	
	public static boolean acceptDatabaseObjects(DatabaseObject parentObject, DatabaseObject object ) {
		try {
			Class<? extends DatabaseObject> parentObjectClass = parentObject.getClass();
			Class<? extends DatabaseObject> objectClass = object.getClass();

			DboExplorerManager manager = Engine.theApp.getDboExplorerManager();
			List<DboGroup> groups = manager.getGroups();
			for (DboGroup group : groups) {
				List<DboCategory> categories = group.getCategories();
				for (DboCategory category : categories) {
					List<DboBeans> beansCategories	= category.getBeans();
					for (DboBeans beansCategory : beansCategories) {
						List<DboBean> beans = beansCategory.getBeans();
						for (DboBean bean : beans) {
							String className = bean.getClassName();
							Class<DatabaseObject> beanClass = GenericUtils.cast(Class.forName(className));
							
							// The bean should derived from DatabaseObject...
							boolean isDatabaseObject = (DatabaseObject.class.isAssignableFrom(beanClass));

							if (isDatabaseObject) {
								// ... and should derived from the specified class
								boolean isFromSpecifiedClass = ((objectClass == null) ||
										((objectClass != null) && (objectClass.isAssignableFrom(beanClass))));
								if (isFromSpecifiedClass) {
                                    // Check parent
                                    boolean bFound = checkParent(parentObjectClass, bean);
                                    if (bFound) {
										// Check technology if needed
										String technology = DboUtils.getTechnology(parentObject, objectClass);
										if (technology != null) {
											Collection<String> acceptedTechnologies = bean.getEmulatorTechnologies();
											if (!acceptedTechnologies.isEmpty() && !acceptedTechnologies.contains(technology)) {
												continue;
											}
										}
										return true;
									}
								}
							}
						}
					}
				}
			}
			return false;
		} catch (Exception e) {
			Engine.logDatabaseObjectManager.error("Unable to load database objects properties.", e);
			return false;
		}
	}
	
	public static boolean acceptDatabaseObjects(DatabaseObject parentObject, Class<? extends DatabaseObject> objectClass, Class<? extends DatabaseObject> folderBeanClass) {
        try {
            Class<? extends DatabaseObject> parentObjectClass = parentObject.getClass();
            DboExplorerManager manager = Engine.theApp.getDboExplorerManager();
            List<DboGroup> groups = manager.getGroups();
            for (DboGroup group : groups) {
                List<DboCategory> categories = group.getCategories();
                for (DboCategory category : categories) {
                    List<DboBeans> beansCategories  = category.getBeans();
                    for (DboBeans beansCategory : beansCategories) {
                        List<DboBean> beans = beansCategory.getBeans();
                        for (DboBean bean : beans) {
                            String className = bean.getClassName();
                            Class<DatabaseObject> beanClass = GenericUtils.cast(Class.forName(className));
                            
                            if (beanClass.equals(objectClass)) {
                                // The bean should derived from DatabaseObject...
                                boolean isDatabaseObject = (DatabaseObject.class.isAssignableFrom(beanClass));
    
                                if (isDatabaseObject) {
                                    // ... and should derived from the specified class
                                    boolean isFromSpecifiedClass = ((folderBeanClass == null) ||
                                            ((folderBeanClass != null) && (folderBeanClass.isAssignableFrom(beanClass))));
                                    if (isFromSpecifiedClass) {
                                        // Check parent
                                        boolean bFound = checkParent(parentObjectClass, bean);
                                        if (bFound) {
                                            // Check technology if needed
                                            String technology = DboUtils.getTechnology(parentObject, objectClass);
                                            if (technology != null) {
                                                Collection<String> acceptedTechnologies = bean.getEmulatorTechnologies();
                                                if (!acceptedTechnologies.isEmpty() && !acceptedTechnologies.contains(technology)) {
                                                    continue;
                                                }
                                            }
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            Engine.logDatabaseObjectManager.error("Unable to load database objects properties.", e);
            return false;
        }
    }
	
	public Project getProjectByName(String projectName) throws EngineException {
		try {
			return getOriginalProjectByName(projectName).clone();
		} catch (CloneNotSupportedException e) {
			throw new EngineException("Exception on getProjectByName", e);
		}
	}
	
	public void clearCache(Project project) {
		String name = project.getName();
		synchronized (projects) {
			if (projects.get(name) == project) {
				projects.remove(name);
				RestApiManager.getInstance().removeUrlMapper(name);
				MobileBuilder.releaseBuilder(project);
			}
		}
	}
	
	public void clearCache(String projectName) {
		synchronized (projects) {
			Project project = projects.remove(projectName);
			RestApiManager.getInstance().removeUrlMapper(projectName);
			MobileBuilder.releaseBuilder(project);
		}
	}
	
	public void clearCacheIfSymbolError(String projectName) throws Exception {
		synchronized (projects) {
			if(projects.containsKey(projectName)) {
				if (symbolsProjectCheckUndefined(projectName)) {
					Project project = projects.remove(projectName);
					RestApiManager.getInstance().removeUrlMapper(projectName);
					MobileBuilder.releaseBuilder(project);
				}
			}	
		}
	}

	public void buildCar(String projectName) {
		try {
			CarUtils.makeArchive(projectName);
		} catch (EngineException e) {
			Engine.logDatabaseObjectManager.error("Build car failed!", e);
		}
	}

	public boolean existsProject(String projectName) {
		File file = studioProjects.getProject(projectName);
		if (file == null) {
			file = new File(Engine.PROJECTS_PATH + "/" + projectName);
		}
		return file.exists();
	}

	public void deleteProject(String projectName) throws EngineException {
		try {
			deleteProject(projectName, DeleteProjectOption.createBackup);
		} catch (Exception e) {
			throw new EngineException("Unable to delete the project \"" + projectName + "\".", e);
		}
	}

	public void deleteProject(String projectName, boolean bCreateBackup) throws EngineException {
		try {
			deleteProject(projectName, bCreateBackup ? DeleteProjectOption.createBackup : null);
		} catch (Exception e) {
			throw new EngineException("Unable to delete the project \"" + projectName + "\".", e);
		}
	}
	
	public void deleteProject(String projectName, boolean bCreateBackup, boolean bDataOnly)
			throws EngineException {
		deleteProject(projectName,
			bCreateBackup ? DeleteProjectOption.createBackup : null,
			bDataOnly ? DeleteProjectOption.dataOnly : null);
	}
	
	public void deleteProject(String projectName, DeleteProjectOption... options) throws EngineException {
		boolean bCreateBackup = DeleteProjectOption.createBackup.as(options);
		boolean bDataOnly = DeleteProjectOption.dataOnly.as(options);
		boolean bPreserveEclipe = DeleteProjectOption.preserveEclipse.as(options);
		boolean bPreserveVCS = DeleteProjectOption.preserveVCS.as(options);
		try {
			// Remove all pooled related contexts in server mode
			if (Engine.isEngineMode()) {
				// Bugfix #1659: do not call getProjectByName() if the migration
				// process is ongoing!
				if (!(Thread.currentThread() instanceof MigrationJob)) {
					Project projectToDelete = Engine.theApp.databaseObjectsManager.getProjectByName(projectName);
					for (Connector connector : projectToDelete.getConnectorsList()) {
						Engine.theApp.contextManager.removeDevicePool(connector.getQName());
					}
					Engine.theApp.contextManager.removeAll("/" + projectName);
				}
			}
			File projectDir = new File(Engine.projectDir(projectName));
			File removeDir = projectDir;
			
			if (!bDataOnly && !bPreserveEclipe && !bPreserveVCS) {
				StringBuilder sb = new StringBuilder(Engine.PROJECTS_PATH + "/_remove_" + projectName);
				while ((removeDir = new File(sb.toString())).exists()) {
					sb.append('_');
				}
				if (!projectDir.renameTo(removeDir)) {
					throw new EngineException("Unable to rename project's directory. It may be locked.");
				}
			}
			
			if (bCreateBackup && EnginePropertiesManager.getPropertyAsBoolean(PropertyName.ZIP_BACKUP_OLD_PROJECT)) {
				Engine.logDatabaseObjectManager.info("Making backup of project \"" + projectName + "\"");
				makeProjectBackup(projectName, removeDir);
			}

			if (bDataOnly) {
				Engine.logDatabaseObjectManager.info("Deleting __datas for  project \"" + projectName + "\"");
				String dataDir = Engine.PROJECTS_PATH + "/" + projectName + "/_data";
				deleteDir(new File(dataDir));

				Engine.logDatabaseObjectManager
						.info("Deleting __private for  project \"" + projectName + "\"");
				String privateDir = Engine.PROJECTS_PATH + "/" + projectName + "/_private";
				deleteDir(new File(privateDir));
			} else {
				Engine.logDatabaseObjectManager.info("Deleting  project \"" + projectName + "\"");
				if (!bPreserveEclipe && !bPreserveVCS) {
					deleteDir(removeDir);
				} else {
					for (File f: removeDir.listFiles((dir, name) -> {
						if (bPreserveEclipe && (name.equals(".project") || name.equals(".settings"))) {
							return false;
						}
						if (bPreserveVCS && (name.equals(".git") || name.equals(".svn"))) {
							return false;
						}
						return true;
					})) {
						deleteDir(f);
					};
				}
			}

			clearCache(projectName);
		} catch (Exception e) {
			throw new EngineException("Unable to delete" + (bDataOnly ? " datas for" : "") + " project \""
					+ projectName + "\".", e);
		}
	}

	public void deleteProjectAndCar(String projectName) throws EngineException {
		try {
			deleteProject(projectName);

			String projectArchive = Engine.projectDir(projectName) + ".car";
			deleteDir(new File(projectArchive));
		} catch (Exception e) {
			throw new EngineException("Unable to delete the project \"" + projectName + "\".", e);
		}
	}

	public static void deleteDir(File dir) throws IOException {
		Engine.logDatabaseObjectManager.trace("Deleting the directory \"" + dir.getAbsolutePath() + "\"");
		if (dir.exists()) {
			if (dir.isDirectory()) {
				boolean deleted = FileUtils.deleteQuietly(dir);				
				if (deleted) {
					Engine.logDatabaseObjectManager.debug("Deleting the file \"" + dir.getAbsolutePath() + "\" by a native command.");
					return;
				}
				
				String[] children = dir.list();
				File subdir;
				for (int i = 0; i < children.length; i++) {
					subdir = new File(dir, children[i]);
					deleteDir(subdir);
				}
				if (!dir.delete())
					throw new IOException("Unable to delete the directory \"" + dir.getAbsolutePath() + "\".");
			} else {
				Engine.logDatabaseObjectManager.trace("Deleting the file \"" + dir.getAbsolutePath() + "\"");
				if (!dir.delete())
					throw new IOException("Unable to delete the file \"" + dir.getAbsolutePath() + "\".");
			}
		}
	}

	public void makeProjectBackup(String projectName, File projectDir) throws EngineException {
		try {
			
			if (projectDir.exists()) {
				Calendar calendar = Calendar.getInstance();
				int iMonth = (calendar.get(Calendar.MONTH) + 1);
				int iDay = calendar.get(Calendar.DAY_OF_MONTH);
				String day = (iDay < 10 ? "0" + iDay : iDay + "");
				String month = (iMonth < 10 ? "0" + iMonth : iMonth + "");
				String stamp = calendar.get(Calendar.YEAR) + "-" + month + "-" + day;
				String projectArchiveFilename = Engine.PROJECTS_PATH + "/" + projectName + "_" + stamp + ".zip";
	
				File file = new File(projectArchiveFilename);
				int i = 1;
				while (file.exists()) {
					projectArchiveFilename = Engine.PROJECTS_PATH + "/" + projectName + "_" + stamp + "_" + i
							+ ".zip";
					file = new File(projectArchiveFilename);
					i++;
				}
	
				// Creating backup
				ZipUtils.makeZip(projectArchiveFilename, projectDir.getPath(), projectName, Arrays.asList(
						new File(projectDir, "_private"),
						new File(projectDir, ".git"),
						new File(projectDir, ".svn")
				));
			} else {
				Engine.logEngine.warn("Cannot make project archive, the folder '" + projectDir + "' doesn't exist.");
			}
		} catch (Exception e) {
			throw new EngineException(
					"Unable to make backup archive for the project \"" + projectName + "\".", e);
		}
	}

	public Project updateProject(File projectFile) throws EngineException {
		return updateProject(projectFile.getAbsolutePath());
	}
	
	public Project updateProject(String projectFileName) throws EngineException {
		try {
			boolean isArchive = false, needsMigration = false;
			Project project = null;

			Engine.logDatabaseObjectManager.trace("DatabaseObjectsManager.updateProject() - projectFileName  :  " + projectFileName);
			File projectFile = new File(projectFileName);
			Engine.logDatabaseObjectManager.trace("DatabaseObjectsManager.updateProject() - projectFile.exists()  :  " + projectFile.exists());
			
			if (projectFile.exists()) {
				String projectName = getProjectName(projectFile);
				if (projectFileName.endsWith(".car")) {
					isArchive = true;
				}

				if (projectName != null) {
					needsMigration = needsMigration(projectFile);

					if (isArchive) {
						// Deploy project (will backup project and perform the
						// migration through import if necessary)
						project = deployProject(projectFileName, needsMigration);
					} else {
						if (needsMigration) {
							Engine.logDatabaseObjectManager.debug("Project '" + projectName
									+ "' needs to be migrated");

							// Delete project's data only (will backup project)
							deleteProject(projectName, true, true);

							// Import project (will perform the migration)
							project = importProject(projectFileName);

							Engine.logDatabaseObjectManager.info("Project '" + projectName
									+ "' has been migrated");
						} else {
							Engine.logDatabaseObjectManager.debug("Project '" + projectName
									+ "' is up to date");
						}
					}
				}
			} else{
				//Added by julienda - 10/09/2012
				Engine.logDatabaseObjectManager.trace("DatabaseObjectsManager.updateProject() - projectFileName :  "+projectFileName);
					//Get the correct archive file (path)
					String archiveFileProject =  ZipUtils.getArchiveName(projectFileName);
					
					if(archiveFileProject == null)
						throw new EngineException("File \"" + projectFileName + "\" is missing");
					else
						//Call method with the correct archive (path)
						updateProject(new File(new File (projectFileName).getParent(), archiveFileProject).getPath());
					
					Engine.logDatabaseObjectManager.trace("DatabaseObjectsManager.updateProject() - archiveFileProject  :  "+archiveFileProject);		
			}
				
	
			return project;
		} catch (Exception e) {
			throw new EngineException("Unable to update the project from the file \"" + projectFileName
					+ "\".", e);
		}
	}

	public void exportProject(Project project) throws EngineException {
		String projectName = project.getName();

		// Export project
		Engine.logDatabaseObjectManager.debug("Saving project \"" + projectName + "\" to XML file ...");
		String exportedProjectFileName = Engine.projectFile(projectName).getAbsolutePath();
		CarUtils.exportProject(project, exportedProjectFileName);
		RestApiManager.getInstance().putUrlMapper(project);
		Engine.logDatabaseObjectManager.info("Project \"" + projectName + "\" saved!");
	}

	public Project deployProject(String projectArchiveFilename, boolean bForce) throws EngineException {
		return deployProject(projectArchiveFilename, null, bForce);
	}

	public Project deployProject(String projectArchiveFilename, String targetProjectName, boolean bForce)
			throws EngineException {
		return deployProject(projectArchiveFilename, targetProjectName, bForce, false);
	}
	
	public Project deployProject(String projectArchiveFilename, String targetProjectName, boolean bForce, boolean keepOldReferences)
			throws EngineException {
		String archiveProjectName, projectDirPath;
		try {
			archiveProjectName = ZipUtils.getProjectName(projectArchiveFilename);
			if (archiveProjectName == null) {
				String message = "Unable to deploy the project from the file \"" + projectArchiveFilename
						+ "\". Inconsistency between the archive and project names.";
				Engine.logDatabaseObjectManager.error(message);
				throw new EngineException(message);
			}
			
			Engine.logDatabaseObjectManager.trace("Deploying project from \"" + projectArchiveFilename + "\"");
			Engine.logDatabaseObjectManager.trace("- archiveProjectName: " + archiveProjectName);
			Engine.logDatabaseObjectManager.trace("- targetProjectName: " + targetProjectName);
			
			if (targetProjectName == null && projectArchiveFilename != null) {
				targetProjectName = archiveProjectName;
			}
			
			// Handle non-normalized project name here (fix ticket #788 : Can
			// not import project 213.car)
			String normalizedProjectName = StringUtils.normalize(targetProjectName);
			if (!targetProjectName.equals(normalizedProjectName)) {
				targetProjectName = "project_" + normalizedProjectName;
			}
			
			File existingProject = Engine.projectFile(targetProjectName);
			projectDirPath = existingProject.getParent();

			Engine.logDatabaseObjectManager.info("Deploying the project \"" + archiveProjectName + "\" to \"" + projectDirPath + "\"");
			try {
				if (existingProject.exists()) {
					if (bForce) {
						// Deleting existing project if any
						deleteProject(targetProjectName,
								DeleteProjectOption.createBackup,
								DeleteProjectOption.preserveEclipse,
								DeleteProjectOption.preserveVCS);
					} else {
						Engine.logDatabaseObjectManager.info("Project \"" + targetProjectName + "\" has already been deployed.");
						return null;
					}
				}

				new File(projectDirPath).mkdir();
				Engine.logDatabaseObjectManager.debug("Project directory created: " + projectDirPath);
			} catch (Exception e) {
				throw new EngineException("Unable to create the project directory \"" + projectDirPath + "\".", e);
			}

			// Decompressing Convertigo archive
			Engine.logDatabaseObjectManager.debug("Analyzing the archive entries: " + projectArchiveFilename);
			ZipUtils.expandZip(projectArchiveFilename, projectDirPath, archiveProjectName);
		} catch (Exception e) {
			throw new EngineException("Unable to deploy the project from the file \"" + projectArchiveFilename + "\".", e);
		}

		try {
			File xmlFile = new File(projectDirPath + "/" + archiveProjectName + ".xml");
			// Rename project and files if necessary
			if (!targetProjectName.equals(archiveProjectName)) {
				xmlFile = ProjectUtils.renameProjectFile(xmlFile, targetProjectName, keepOldReferences);
//				File dir = new File(projectDirPath);
//				if (dir.isDirectory()) {
//					// rename project directory
//					File newdir = new File(Engine.PROJECTS_PATH + "/" + targetProjectName);
//					dir.renameTo(newdir);
//					Engine.logDatabaseObjectManager.debug("Project directory renamed to: " + newdir);
//					
//					// rename project in xml file
//					if (keepOldReferences) {
//						ProjectUtils.renameProjectFile(Engine.PROJECTS_PATH, archiveProjectName, targetProjectName);
//					}
//					else {
//						ProjectUtils.renameXmlProject(Engine.PROJECTS_PATH, archiveProjectName, targetProjectName);		
//					}
//					Engine.logDatabaseObjectManager.debug("Project renamed from \"" + archiveProjectName
//							+ "\" to \"" + targetProjectName + "\"");
//					
//					// rename/modify project wsdl & xsd files (for old car < 7.0.0)
//					try {
//						ProjectUtils.renameXsdFile(Engine.PROJECTS_PATH, archiveProjectName, targetProjectName);
//						ProjectUtils.renameWsdlFile(Engine.PROJECTS_PATH, archiveProjectName, targetProjectName);
//						Engine.logDatabaseObjectManager.debug("Project wsdl & xsd files modified");
//					} catch (Exception e) {
//					}
//					
//					// update transaction schema files with new project's name
//					List<Replacement> replacements = new ArrayList<Replacement>();
//					replacements.add(new Replacement("/"+archiveProjectName, "/"+targetProjectName));
//					replacements.add(new Replacement(archiveProjectName+"_ns", targetProjectName+"_ns"));
//					ArrayList<File> deep = CarUtils.deepListFiles(Engine.PROJECTS_PATH + "/" + targetProjectName + "/xsd/internal", ".xsd");
//					if (deep != null) {
//						for (File schema : deep) {
//							try {
//								ProjectUtils.makeReplacementsInFile(replacements, schema.getAbsolutePath().toString());
//								Engine.logDatabaseObjectManager.debug("Successfully updated schema file \""+ schema.getAbsolutePath() +"\"");
//							} catch (Exception e) {
//								Engine.logDatabaseObjectManager.warn("Unable to update schema file \""+ schema.getAbsolutePath() +"\"");
//							}
//						}
//					}
//				}
			}
			
			if (getProjectLoadingData().projectName == null) {
				getProjectLoadingData().projectName = targetProjectName;
			}
			
			// Import project (will perform the migration)
			Project project = importProject(xmlFile);

			// Rename connector's directory under traces directory if needed
			// (name should be normalized since 4.6)
			File tracesDir = new File(projectDirPath + "/Traces");
			if (tracesDir.isDirectory()) {
				File connectorDir;
				String connectorName;
				File[] files = tracesDir.listFiles();
				for (int i = 0; i < files.length; i++) {
					connectorDir = files[i];
					if (connectorDir.isDirectory()) {
						connectorName = connectorDir.getName();
						if (!StringUtils.isNormalized(connectorName)) {
							if (!connectorDir.renameTo(new File(Engine.PROJECTS_PATH + "/" + targetProjectName
									+ "/Traces/" + StringUtils.normalize(connectorName))))
								Engine.logDatabaseObjectManager.warn("Could not rename \"" + connectorName
										+ "\" directory under \"Traces\" directory.");
						}
					}
				}
			}

			Engine.logDatabaseObjectManager.info("Project \"" + targetProjectName + "\" deployed!");
			return project;
		} catch (Exception e) {
			throw new EngineException("Unable to deploy the project from the file \"" + projectArchiveFilename
					+ "\".", e);
		}
	}

	public Project deployProject(String projectArchiveFilename, boolean bForce, boolean bAssembleXsl)
			throws EngineException {
		Project project = deployProject(projectArchiveFilename, bForce);
		String projectName = project.getName();

		if (bAssembleXsl) {
			String projectDir = Engine.projectDir(projectName);
			String xmlFilePath = projectDir + "/" + projectName + ".xml";
			try {
				Document document = XMLUtils.loadXml(xmlFilePath);
				Element rootElement = document.getDocumentElement();
				NodeList sheets = rootElement.getElementsByTagName("sheet");

				NodeList properties;
				Element prop;
				String sheetUrl;
				Document xslDom;
				NodeList includes;
				// for each sheet, read file and assemble xsl includes
				for (int i = 0; i < sheets.getLength(); i++) {
					// retrieve sheet url
					properties = ((Element) sheets.item(i)).getElementsByTagName("property");
					prop = (Element) XMLUtils.findNodeByAttributeValue(properties, "name", "url");
					sheetUrl = projectDir
							+ "/"
							+ ((Element) prop.getElementsByTagName("java.lang.String").item(0))
									.getAttribute("value");
					// read file
					xslDom = XMLUtils.loadXml(sheetUrl);
					if (Engine.logDatabaseObjectManager.isTraceEnabled())
						Engine.logDatabaseObjectManager.trace("XSL file read: " + sheetUrl + "\n"
								+ XMLUtils.prettyPrintDOM(xslDom));
					includes = xslDom.getDocumentElement().getElementsByTagName("xsl:include");
					Engine.logDatabaseObjectManager.trace(includes.getLength()
							+ " \"xsl:include\" tags in the XSL file");
					// for each include element, include the xsl elemnts
					for (int j = 0; j < includes.getLength(); j++) {
						includeXsl(projectDir, (Element) includes.item(j));
						j--;
						// decrement variable j because includeXsl function
						// removes "includes.item(j)" Node from
						// its parent Node, and so it is also removed from the
						// "includes" NodeList
					}
					// save the xsl dom in the xsl file
					if (Engine.logDatabaseObjectManager.isTraceEnabled())
						Engine.logDatabaseObjectManager
								.trace("XSL file saved after including include files: \n"
										+ XMLUtils.prettyPrintDOM(xslDom));
					XMLUtils.saveXml(xslDom, sheetUrl);
				}

			} catch (Exception e) {
				deleteProject(projectName);
				throw new EngineException("Unable to assemble the XSL files from project \"" + projectName
						+ "\".", e);
			}
		}

		return project;
	}

	private void includeXsl(String projectDir, Element includeElem) throws ParserConfigurationException,
			SAXException, IOException {
		Element parentElem = (Element) includeElem.getParentNode();
		Document doc = includeElem.getOwnerDocument();

		String href = includeElem.getAttribute("href");
		String xslFile = href.startsWith("../../xsl/") ? Engine.XSL_PATH
				+ href.substring(href.lastIndexOf("/")) : projectDir + "/" + href;
		Document document = XMLUtils.loadXml(xslFile);
		NodeList xslElements = document.getDocumentElement().getChildNodes();
		Node xslElem, importedXslElem;
		for (int i = 0; i < xslElements.getLength(); i++) {
			xslElem = xslElements.item(i);
			if (xslElem.getNodeType() == Node.ELEMENT_NODE && xslElem.getNodeName().equals("xsl:include")) {
				String fileDir = xslFile.substring(0, xslFile.lastIndexOf("/"));
				includeXsl(fileDir, (Element) xslElem);
				// decrement variable i because includeXsl function removes
				// "xslElem" Node from
				// its parent Node and so it is also removed from the
				// "xslElements" NodeList
				i--;
			} else {
				importedXslElem = doc.importNode(xslElem, true);
				parentElem.appendChild(importedXslElem);
			}
		}
		parentElem.removeChild(includeElem);
	}
	
	public Project importProject(File importFileName) throws EngineException {
		return importProject(importFileName.getAbsolutePath());
	}

	public Project importProject(String importFileName) throws EngineException {
		try {
			File file = new File(importFileName);
			if (!file.exists() && importFileName.endsWith(".xml")) {
				importFileName = new File(file.getParentFile(), "c8oProject.yaml").getAbsolutePath();
			}
			Project project = importProject(importFileName, null);
			CouchDbManager.syncDocument(project);
			return project;
		} catch (Exception e) {
			throw new EngineException("An error occured while importing project", e);
		}
	}

	public Project importProject(Document document) throws EngineException {
		try {
			return importProject(null, document);
		} catch (Exception e) {
			throw new EngineException("An error occured while importing project", e);
		}
	}
	
	static public String getProjectName(File projectFile) throws EngineException {
		String projectName = null;
		if (projectFile.exists()) {
			if (projectFile.getName().equals("c8oProject.yaml")) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(projectFile), "UTF-8"))) {
					String line = br.readLine();
					Matcher m = pYamlProjectName.matcher("");
					while (line != null && projectName == null) {
						m.reset(line);
						if (m.matches()) {
							projectName = m.group(1);
							line = br.readLine();
						} else {
							line = null;
						}
					}
				} catch (Exception e) {
					throw new EngineException("Unable to parse the yaml: " + projectFile.getAbsolutePath(), e);
				}
			} else if (projectFile.getName().endsWith(".car")) {
				try {
					projectName = ZipUtils.getProjectName(projectFile.getAbsolutePath());
				} catch (IOException e) {
				}
			}
			if (projectName == null) {
				Matcher m = pProjectName.matcher(projectFile.getName());
				if (m.matches()) {
					projectName = m.group(1);
				}
			}
		}
		return projectName;
	}
	
	static public String getProjectVersion(File projectFile) throws EngineException {
		final String[] version = { null };
		if (projectFile.exists()) {
			if (projectFile.getName().equals("c8oProject.yaml")) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(projectFile), "UTF-8"))) {
					String line = br.readLine();
					Matcher m = pYamlProjectVersion.matcher("");
					while (line != null && version[0] == null) {
						m.reset(line);
						if (m.matches()) {
							if (m.group(1) != null) {
								version[0] = m.group(2);
							} else {
								line = br.readLine();							
							}
						} else {
							line = null;
						}
					}
				} catch (Exception e) {
					throw new EngineException("Unable to parse the yaml: " + projectFile.getAbsolutePath(), e);
				}
			} else if (projectFile.getName().endsWith(".xml")) {
				try {
					XMLUtils.saxParse(projectFile, new DefaultHandler() {
	
						@Override
						public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
							if ("convertigo".equals(qName)) {
								// since 6.0.6 (fix #2804)
								version[0] = attributes.getValue("beans");
							} else if ("project".equals(qName)) {
								String projectVersion = attributes.getValue("version");
								if (projectVersion != null) {
									// before 6.0.6
									version[0] = projectVersion;
								}
							}
							throw new SAXException("stop");
						}
	
					});
				} catch (SAXException e) {
					if (!"stop".equals(e.getMessage())) {
						throw new EngineException("Unable to find the project version", e);
					}
				} catch (IOException e) {
					throw new EngineException("Unable to parse the xml: " + projectFile.getAbsolutePath(), e);
				}
			}
		}
		return version[0];
	}
	
	private boolean needsMigration(File projectFile) throws EngineException {
		if (projectFile != null) {
			String version = getProjectVersion(projectFile);
			if (version == null) {
				throw new EngineException("Unable to retrieve project's version from \"" + projectFile + "\".");
			}
			String currentVersion = com.twinsoft.convertigo.beans.Version.version;
			if (VersionUtils.compare(version, currentVersion) < 0) {
				Engine.logDatabaseObjectManager.warn("Project from '" + projectFile + "': migration to " + currentVersion + " beans version is required");
				return true;
			}
		}
		return false;
	}

	private Project importProject(String importFileName, Document document) throws EngineException {
		try {
			Engine.logDatabaseObjectManager.info("Importing project ...");
			if (importFileName != null) {
				File importFile = new File(importFileName);
				if (importFile.getName().equals("c8oProject.yaml")) {
					document = YamlConverter.readYaml(importFile);
					document = BeansDefaultValues.unshrinkProject(document);
				} else {
					document = XMLUtils.loadXml(importFile);
				}
			}

			// Performs necessary XML migration
			Element projectNode = performXmlMigration(document);
			
			Element rootElement = document.getDocumentElement();
			Element projectElement = (Element) XMLUtils.findChildNode(rootElement, Node.ELEMENT_NODE);

			// Retrieve project version
			String version = rootElement.getAttribute("beans");
			projectElement.setAttribute("version", version);

			// Retrieve project name
			NodeList properties = projectElement.getElementsByTagName("property");
			Element pName = (Element) XMLUtils.findNodeByAttributeValue(properties, "name", "name");
			String projectName = (String) XMLUtils.readObjectFromXml((Element) XMLUtils.findChildNode(pName, Node.ELEMENT_NODE));

			projectLoadingDataThreadLocal.remove();
			getProjectLoadingData().projectName = projectName;
			
			// Import will perform necessary beans migration (see deserialization)
			Project project = (Project) importDatabaseObject(projectNode, null);
			project.undefinedGlobalSymbols = getProjectLoadingData().undefinedGlobalSymbol;
			
			synchronized (projects) {
				projects.put(project.getName(), project);
				RestApiManager.getInstance().putUrlMapper(project);
				MobileBuilder.initBuilder(project);
			}

			// Creates xsd/wsdl files (Since 4.6.0)
			performWsMigration(version, projectName);

			// Performs POST migration
			performPostMigration(version, projectName);
			
			// Export the project (Since 4.6.0)
			String currentVersion = com.twinsoft.convertigo.beans.Version.version;
			if (VersionUtils.compare(version, currentVersion) < 0) {

				// Since 4.6 export project to its xml file
				// Only export project for versions older than 4.0.1
				// TODO: Migration to 4.0.1 (parent bean handles children order
				// (priorities))!!
				if (VersionUtils.compare(version, "4.0.1") >= 0) {
					exportProject(project);
				} else {
					Engine.logDatabaseObjectManager
							.error("Project \""
									+ projectName
									+ "\" has been partially migrated. It may not work properly. Please import it trought the Studio and export/upload it again.");
				}
			}
			
			if (project.undefinedGlobalSymbols) {
				Engine.logDatabaseObjectManager.error("Project \"" + projectName + "\" contains undefined global symbols: " + symbolsGetUndefined(projectName));
			}
			
			Engine.logDatabaseObjectManager.info("Project \"" + projectName + "\" imported!");

			return project;
		} catch (Exception e) {
			throw new EngineException("Unable to import the project from \"" + importFileName + "\".", e);
		}
	}

	private Element performXmlMigration(Document document) throws EngineException {
		try {
			Element rootElement = document.getDocumentElement();

			Element projectNode = (Element) XMLUtils.findChildNode(rootElement, Node.ELEMENT_NODE);

			String version = projectNode.getAttribute("version");
			if ("".equals(version)) {
				version = rootElement.getAttribute("beans");
			}

			// Migration to version 3.0.0 schema
			if ((version.startsWith("1.")) || (version.startsWith("2."))) {
				Engine.logDatabaseObjectManager.info("XML project's file migration to 3.0.0 schema ...");

				projectNode = Migration3_0_0.migrate(document, projectNode);

				if (Engine.logDatabaseObjectManager.isTraceEnabled())
					Engine.logDatabaseObjectManager.trace("XML migrated to v3.0:\n"
							+ (XMLUtils.prettyPrintDOM(document)));
			}

			// Migration to version 4.6.0 schema for CEMS 5.0.0
			if (VersionUtils.compare(version, "4.6.0") < 0) {
				Engine.logDatabaseObjectManager.info("XML project's file migration to 4.6.0 schema ...");

				projectNode = Migration5_0_0.migrate(document, projectNode);

				if (Engine.logDatabaseObjectManager.isTraceEnabled())
					Engine.logDatabaseObjectManager.trace("XML migrated to v4.6:\n"
							+ (XMLUtils.prettyPrintDOM(document)));

				Engine.logDatabaseObjectManager.info("Project's XML file migrated!");
			}

			// Migration to version 5.0.3 schema for CEMS 5.0.4
			if (VersionUtils.compare(version, "5.0.3") < 0) {
				Engine.logDatabaseObjectManager.info("XML project's file migration to 5.0.3 schema ...");

				projectNode = Migration5_0_4.migrate(document, projectNode);

				if (Engine.logDatabaseObjectManager.isTraceEnabled())
					Engine.logDatabaseObjectManager.trace("XML migrated to v5.0.3:\n"
							+ (XMLUtils.prettyPrintDOM(document)));

				Engine.logDatabaseObjectManager.info("Project's XML file migrated!");
			}

			// Migration to version m001
			if (VersionUtils.compareMigrationVersion(version, ".m001") < 0) {
				Engine.logDatabaseObjectManager.info("XML project's file migration to m001 schema ...");

				projectNode = Migration001.migrate(document, projectNode);

				if (Engine.logDatabaseObjectManager.isTraceEnabled())
					Engine.logDatabaseObjectManager.trace("XML migrated to m001:\n"
							+ (XMLUtils.prettyPrintDOM(document)));

				Engine.logDatabaseObjectManager.info("Project's XML file migrated!");
			}
			
			// Migration to version 7.0.0 (mobile application)
			if (VersionUtils.compare(version, "7.0.0") < 0) {
				Engine.logDatabaseObjectManager.info("XML project's file migration to 7.0.0 schema (mobile application)...");

				projectNode = Migration7_0_0.migrate(document, projectNode);

				if (Engine.logDatabaseObjectManager.isTraceEnabled())
					Engine.logDatabaseObjectManager.trace("XML migrated to v7.0.0:\n"
							+ (XMLUtils.prettyPrintDOM(document)));

				Engine.logDatabaseObjectManager.info("Project's XML file migrated!");
			}
			
			return projectNode;
		} catch (Exception e) {
			throw new EngineException("Unable to perform XML migration for project", e);
		}
	}

	private void performPostMigration(String version, String projectName) {
		if (VersionUtils.compare(version, "6.2.0") < 0) {
			try {
				//Project project = getProjectByName(projectName); //recursivity issue since #4780
				Project project = getOriginalProjectByName(projectName, false);
				for (Sequence sequence : project.getSequencesList()) {
					// Modify source's xpath for steps which have a source on a ReadFileStep
					replaceSourceXpath(version, sequence, sequence.getSteps());
				}
				
			} catch (Exception e) {
				Engine.logDatabaseObjectManager.error(
						"An error occured while performing 6.2.0 migration for project '" + projectName + "'", e);
			}
		}
		
		if (VersionUtils.compare(version, "7.0.0") < 0) {
			// !! Studio mode only !!
			// Project must be migrated by hand through: Studio import
			if (Engine.isStudioMode()) {
				// Project's xsd/wsdl no more based on file (Since 7.0.0)
				Migration7_0_0.migrate(projectName);
			}
		}
		
		if (VersionUtils.compare(version, "7.4.0") < 0) {
			// !! Studio mode only !!
			// Project must be migrated by hand through: Studio import
			if (Engine.isStudioMode()) {
				// Change MobilePlatform config.xml
				Migration7_4_0.migrate(projectName);
			}
		}
		
	}
	
	private boolean performWsMigration(String version, String projectName) {
		/** Part of 4.6.0 migration : creates and update XSD/WSDL static files **/

		if (VersionUtils.compare(version, "4.6.0") < 0) {
			try {
					// Retrieve a !clone! of project to perform update
					Project project = getProjectByName(projectName);

					for (Connector connector : project.getConnectorsList()) {
						// Retrieve backup wsdlTypes and store Transaction's schema
						for (Transaction transaction : connector.getTransactionsList()) {
							try {
								String xsdTypes = transaction.migrateToXsdTypes();
								transaction.writeSchemaToFile(xsdTypes);
								Engine.logDatabaseObjectManager
										.info("Internal schema stored for \""
												+ transaction.getName() + "\" transaction");
							} catch (Exception e) {
								Engine.logDatabaseObjectManager.error(
										"An error occured while writing schema to file for \""
											+ transaction.getName() + "\" transaction");
							}
						}
					}

					// Fix sequence's steps sources
					for (Sequence sequence : project.getSequencesList()) {
						try {
							List<Step> steps = sequence.getSteps();

							// Replace source's xpath
							// replace ./xxx by
							// ./transaction/document/xxx or by
							// ./sequence/document/xxx
							replaceSourceXpath(version, sequence, steps);

							Engine.logDatabaseObjectManager
									.info("Step sources updated for sequence \""
											+ sequence.getName() + "\"");
						} catch (Exception e) {
							Engine.logDatabaseObjectManager.error(
									"An error occured while updating step sources for sequence \""
									+ sequence.getName() + "\"");
						}
					}


			} catch (Exception e) {
				Engine.logDatabaseObjectManager.error(
						"An error occured while updating project '" + projectName + "' for XSD", e);
				return false;
			}
		}
		
		return true;
	}

	private void replaceSourceXpath(String version, Sequence sequence, List<Step> stepList) {
		for (Step step : stepList) {
			if (step instanceof IStepSourceContainer) {
				replaceXpath(version, sequence, ((IStepSourceContainer) step).getSourceDefinition());
			} else if (step instanceof XMLGenerateDatesStep) {
				replaceXpath(version, sequence, ((XMLGenerateDatesStep) step).getStartDefinition());
				replaceXpath(version, sequence, ((XMLGenerateDatesStep) step).getStopDefinition());
				replaceXpath(version, sequence, ((XMLGenerateDatesStep) step).getDaysDefinition());
			} else if (step instanceof XMLActionStep) {
				for (int i = 0; i < ((XMLActionStep) step).getSourcesDefinitionSize(); i++) {
					replaceXpath(version, sequence, ((XMLActionStep) step).getSourceDefinition(i));
				}
			}

			// recurse on children steps
			if (step instanceof StepWithExpressions) {
				replaceSourceXpath(version, sequence, ((StepWithExpressions) step).getSteps());
			}
			// recurse on children variables
			else if (step instanceof RequestableStep) {
				for (StepVariable variable : ((RequestableStep) step).getVariables()) {
					replaceXpath(version, sequence, variable.getSourceDefinition());
				}
			}
		}
	}

	private void replaceXpath(String version, Sequence sequence, List<String> definition) {
		if (definition.size() > 0) {
			String xpath = definition.get(1);
			if (xpath.startsWith("./")) {
				Long key = new Long(definition.get(0));
				Step sourceStep = sequence.loadedSteps.get(key);
				if (sourceStep != null) {
					if (VersionUtils.compare(version, "4.6.0") < 0) {
						if (sourceStep instanceof RequestableStep) {
							String replace = (sourceStep instanceof TransactionStep) ? "transaction" : "sequence";
							xpath = xpath.replaceFirst("./", "./" + replace + "/document/");
							definition.set(1, xpath);
						}
					}
					else if (VersionUtils.compare(version, "6.2.0") < 0) {
						if (sourceStep instanceof ReadFileStep) {
							xpath = ((ReadFileStep)sourceStep).migrateSourceXpathFor620(xpath);
							definition.set(1, xpath);
						}
					}
				}
			}
		}
	}

	private DatabaseObject importDatabaseObject(Node node, DatabaseObject parentDatabaseObject)
			throws EngineException {
		try {
			DatabaseObject databaseObject = DatabaseObject.read(node);
			
			if (parentDatabaseObject != null) {
				parentDatabaseObject.add(databaseObject);
			}

			NodeList childNodes = node.getChildNodes();
			int len = childNodes.getLength();

			Node childNode;
			String childNodeName;

			for (int i = 0; i < len; i++) {
				childNode = childNodes.item(i);

				if (childNode.getNodeType() != Node.ELEMENT_NODE)
					continue;

				childNodeName = childNode.getNodeName();

				if ((!childNodeName.equalsIgnoreCase("property"))
					&& (!childNodeName.equalsIgnoreCase("handlers"))
					&& (!childNodeName.equalsIgnoreCase("wsdltype"))
					&& (!childNodeName.equalsIgnoreCase("docdata"))
					&& (!childNodeName.equalsIgnoreCase("beandata"))) {
						importDatabaseObject(childNode, databaseObject);
				}
			}

			databaseObject.isImporting = false;
			databaseObject.isSubLoaded = true;

			fireDatabaseObjectImported(new DatabaseObjectImportedEvent(databaseObject));
			return databaseObject;
		} catch (Exception e) {
			if (e instanceof EngineException
					&& ((EngineException) e).getCause() instanceof ClassNotFoundException) {
				Engine.logBeans.error("Maybe a database object doesn't exist anymore, drop it", e);
				return null;
			} else
				throw new EngineException("Unable to import the object from the XML node \""
						+ node.getNodeName() + "\".", e);
		}
	}
	
	public void renameProject(Project project, String newName) throws ConvertigoException {
		renameProject(project, newName, false);
	}
	
	public void renameProject(Project project, String newName, boolean keepOldReferences) throws ConvertigoException {
		String oldName = project.getName();
		
		if (oldName.equals(newName)) {
			return;
		}
		// Rename dir
		File file = Engine.projectFile(oldName);
		if (!file.exists()) {
			return;
		}
		
		File dir = file.getParentFile();
		File newDir = new File(dir.getParentFile(), newName);
		if (!dir.renameTo(newDir)) {
			throw new EngineException(
					"Unable to rename the object path \""
							+ dir.getAbsolutePath()
							+ "\" to \""
							+ newDir.getAbsolutePath()
							+ "\".\n This directory already exists or is probably locked by another application.");
		}
		
		try {
			clearCache(project);
			project.setName(newName);
			project.hasChanged = true;
			exportProject(project);
			file = new File(newDir, file.getName());
			ProjectUtils.renameProjectFile(file, newName, keepOldReferences);
	        FileUtils.deleteQuietly(new File(newDir, ".project"));
		} catch (Exception e) {
			throw new ConvertigoException("Failed to rename to project", e);
		}
	}
	
	public String getCompiledValue(String value) throws UndefinedSymbolsException {
		Matcher mFindSymbol = pFindSymbol.matcher(value);
		if (mFindSymbol.find(0)) {
			int start = 0;
			StringBuffer newValue = new StringBuffer();
			Set<String> undefinedSymbols = null;
			do {
				newValue.append(value.substring(start, mFindSymbol.start()));
				
				String name = mFindSymbol.group(1);
				String def = mFindSymbol.group(2);
				
				String symbolValue = symbolsGetValue(name);
				
				if (symbolValue == null) {
					if (def != null) {
						symbolValue = def.replace("\\}", "}");
					} else {
						if (undefinedSymbols == null) {
							undefinedSymbols = new HashSet<String>();
						}
						undefinedSymbols.add(name);
						symbolValue = "";
					}
				}
				
				// Handle symbols in this symbol value
				symbolValue = getCompiledValue(symbolValue);
				
				// Handle environment variables %name%, %name=def%, %name=def\\%%,  
				symbolValue = replaceEnvValues(symbolValue);
				
				newValue.append(symbolValue);
				
				start = mFindSymbol.end();
			} while (mFindSymbol.find(start));

			newValue.append(value.substring(start));
			
			if (undefinedSymbols != null) {
				throw new UndefinedSymbolsException(undefinedSymbols, newValue.toString());
			}
			
			return newValue.toString();
		} else {
			return value;
		}
	}
	
	private String replaceEnvValues(String symbolValue) {
		Matcher mFindEnv = pFindEnv.matcher(symbolValue);
		
		// If there is at least an environment variable
		if (mFindEnv.find(0)) {
			int start = 0;
			// The symbol value re-builded
			StringBuffer newValue = new StringBuffer();
			do {
				// Append the string between the last occurrence and the next one
				newValue.append(symbolValue.substring(start, mFindEnv.start()));
				
				// Get the environment variable name and its default value
				String name = mFindEnv.group(1);
				String def = mFindEnv.group(2);				
				
				String envValue = System.getenv(name);
				
				if (envValue == null) {
					if (def != null) {
						envValue = def.replace("\\%", "%");
					} else {
						// If the environment variable is not defined and there is not default value neither
						Engine.logDatabaseObjectManager.error("The environment variable "  + name + " is undifined.");
						envValue = mFindEnv.group(0);
					}
				}
				
				// Append the value of the environment variable
				newValue.append(envValue);
				
				start = mFindEnv.end();
			} while (mFindEnv.find(start)); // While there is an environment variable
			
			// Append the string between the last occurrence and the end
			newValue.append(symbolValue.substring(start));
			
			return newValue.toString();
			
		} else {
			return symbolValue;
		}		
	}
	
	public Object getCompiledValue(Object propertyObjectValue) throws UndefinedSymbolsException {
		if (propertyObjectValue instanceof String) {
			return getCompiledValue((String) propertyObjectValue);
		} else {
			Set<String> undefinedSymbols = null;
			if (propertyObjectValue instanceof XMLVector<?>) {

				XMLVector<Object> xmlv = GenericUtils.<XMLVector<Object>> cast(propertyObjectValue);

				for (int i = 0; i < xmlv.size(); i++) {
					Object ob = xmlv.get(i);
					Object compiled;

					try {
						compiled = getCompiledValue(ob);
					} catch (UndefinedSymbolsException e) {
						compiled = e.incompletValue();
						if (undefinedSymbols == null) {
							undefinedSymbols = new HashSet<String>();
						}
						undefinedSymbols.addAll(e.undefinedSymbols());
					}

					if (ob != compiled) { // symbol case
						if (xmlv == propertyObjectValue) {
							propertyObjectValue = xmlv = new XMLVector<Object>(xmlv);
						}
						xmlv.set(i, compiled);	
					}
				}
			} else if (propertyObjectValue instanceof SmartType) {
				SmartType smartType = (SmartType) propertyObjectValue;
				if (smartType.isUseExpression()) {
					String expression = smartType.getExpression();
					String compiled;
					try {
						compiled = getCompiledValue(expression);
					} catch (UndefinedSymbolsException e) {
						compiled = (String) e.incompletValue();
						undefinedSymbols = e.undefinedSymbols();
					}
					if (compiled != expression) {
						propertyObjectValue = smartType = smartType.clone();
						smartType.setExpression(compiled);
					}
				}
			}
			
			if (undefinedSymbols != null) {
				throw new UndefinedSymbolsException(undefinedSymbols, propertyObjectValue);
			}
		}
		
		return propertyObjectValue;
	}
	
	private void symbolsInit() {
		globalSymbolsFilePath = System.getProperty(Engine.JVM_PROPERTY_GLOBAL_SYMBOLS_FILE_COMPATIBILITY,  
				System.getProperty(Engine.JVM_PROPERTY_GLOBAL_SYMBOLS_FILE, 
                        Engine.CONFIGURATION_PATH + "/global_symbols.properties")); 		
		Properties prop = new Properties();

		try {
			PropertiesUtils.load(prop, globalSymbolsFilePath);
		} catch (FileNotFoundException e) {
			Engine.logDatabaseObjectManager.warn("The symbols file specified in JVM argument as \""
					+ globalSymbolsFilePath + "\" does not exist! Creating a new one...");
			
			// Create the global_symbols.properties file into the default workspace
			File globalSymbolsProperties = new File(Engine.CONFIGURATION_PATH + "/global_symbols.properties");
			globalSymbolsFilePath = globalSymbolsProperties.getAbsolutePath();
			
			try {
				PropertiesUtils.store(prop, globalSymbolsProperties, "global symbols");
				Engine.logDatabaseObjectManager.info("New global symbols file created: " + globalSymbolsProperties.getAbsolutePath());
			} catch (Exception e1) {
				Engine.logDatabaseObjectManager.error("Error while creating the global_symbols.properties file; symbols won't be calculated.", e1);
				return;
			}
		} catch (IOException e) {
			Engine.logDatabaseObjectManager.error(
					"Error while reading symbols file specified in JVM argument as \"" + globalSymbolsFilePath
							+ "\"; symbols won't be calculated.", e);
			return;
		}
		
		symbolsProperties = new Properties();
		symbolsLoad(prop);
		
		Engine.logEngine.info("Symbols file \"" + globalSymbolsFilePath + "\" loaded!");
	}
	
	private void symbolsLoad(Properties map) {		
		// Enumeration of the properties
		Enumeration<String> propsEnum = GenericUtils.cast(map.propertyNames());
		boolean needUpdate = false;
		while (propsEnum.hasMoreElements()) {
			String propertyName = propsEnum.nextElement();
			try {
				symbolsAdd(propertyName, map.getProperty(propertyName, ""));
				needUpdate = true;
			} catch (Exception e) {
				Engine.logEngine.info("Don't add invalid symbol '" + propertyName + "'", e);
			}
		}
		if (needUpdate) {
			symbolsUpdated();
		}
	}
	
	public void symbolsStore(OutputStream out) throws IOException {
		PropertiesUtils.store(symbolsProperties, out, "global symbols");
	}
	
	public void symbolsUpdate(Properties map, String importAction) {
		File f = new File(globalSymbolsFilePath);
		
		File oldFile = null;
		if (f.exists()) {
			Date date = new Date();
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			
			File parentFile = f.getParentFile();
			oldFile = new File(parentFile, f.getName().replaceAll(".properties", "_" + dateFormat.format(date) + ".properties"));
			
			int i = 1;
			while (oldFile.exists()) {
				oldFile = new File(parentFile, f.getName().replaceAll(".properties", "_" + dateFormat.format(date) + "_" + i + ".properties"));
				i++;
			}
			f.renameTo(oldFile);
		}
		//Remove all symbols & import symbols from file
		if (importAction.equals("clear-import")) {
			symbolsProperties.clear();
			symbolsLoad(map);
		}
		//Add symbols from imported file and merge with existing symbols from server (priority to server if same key)
		if (importAction.equals("priority-server")) {
			symbolsFileImport(map, true);
		}
		//Add symbols from imported file and merge with existing symbols from server (priority to import symbols if same key)
		if (importAction.equals("priority-import")) {
			symbolsFileImport(map, false);
		}
	}
	
	private void symbolsFileImport(Properties map, boolean keepServerSymbols) {		
		// Enumeration of the properties
		Enumeration<String> propsEnum = GenericUtils.cast(map.propertyNames());
		boolean needUpdate = false;
		while (propsEnum.hasMoreElements()) {
			String propertyName = propsEnum.nextElement();
			try {
				if (keepServerSymbols){
					if (!symbolsProperties.containsKey(propertyName)) {
						symbolsAdd(propertyName, map.getProperty(propertyName, ""));
						needUpdate = true;
					}
				} else {
					if (symbolsProperties.containsKey(propertyName)) {
						symbolsProperties.remove(propertyName);
					}
					symbolsAdd(propertyName, map.getProperty(propertyName, ""));
					needUpdate = true;
				}
			} catch (Exception e) {
				Engine.logEngine.info("Don't add invalid symbol '" + propertyName + "'", e);
			}
		}
		if (needUpdate) {
			symbolsUpdated();
		}
	}

	private void symbolsUpdated() {
		try {
			PropertiesUtils.store(symbolsProperties, globalSymbolsFilePath, "global symbols");
		} catch (Exception e) {
			Engine.logEngine.error("Failed to store symbols!", e);
		}
		
		for (Project project : projects.values()) {
			getProjectLoadingData().undefinedGlobalSymbol = false;
			try {
				new WalkHelper() {

					@Override
					protected void walk(DatabaseObject databaseObject) throws Exception {
						databaseObject.updateSymbols();
						super.walk(databaseObject);
					}
					
				}.init(project);
			} catch (Exception e) {
				Engine.logDatabaseObjectManager.error("Failed to update symbols of '" + project.getName() + "' project.", e);
			}
			project.undefinedGlobalSymbols = getProjectLoadingData().undefinedGlobalSymbol;
		}
	}
	
	public void symbolsCreateUndefined(Set<String> symbolsUndefined) throws Exception {
		if (!symbolsUndefined.isEmpty()) {
			for (String symbolUndefined : symbolsUndefined) {
				symbolsAdd(symbolUndefined, "", false);
			}
			symbolsUpdated();
		}
	}
	
	public String symbolsGetValue(String symbolName) {
		return symbolsProperties.getProperty(symbolName);
	}
	
	public Set<String> symbolsGetNames() {
		return Collections.unmodifiableSet(GenericUtils.<Set<String>>cast(symbolsProperties.keySet()));
	}
	
	private void symbolsValidName(String symbolName) {
		if (symbolName == null || symbolName.isEmpty()) {
			throw new IllegalArgumentException("The symbol name must not be empty");
		} else if (pValidSymbolName.matcher(symbolName).find()) {
			throw new IllegalArgumentException("The symbol name must not contain the following caracters '{', '=', or '}'");
		}
	}
	
	private void symbolsAdd(String symbolName, String symbolValue, boolean update) {
		symbolsValidName(symbolName);
		synchronized (symbolsProperties) {
			if (symbolsProperties.containsKey(symbolName)) {
				throw new IllegalArgumentException("The symbol name is already defined");
			} else {
				symbolsProperties.put(symbolName, symbolValue);
			}
			if (update) {
				symbolsUpdated();
			}
		}
	}
	
	public void symbolsAdd(String symbolName, String symbolValue) {
		symbolsAdd(symbolName, symbolValue, true);
	}

	public void symbolsEdit(String oldSymbolName, String symbolName, String symbolValue) {
		symbolsValidName(symbolName);
		synchronized (symbolsProperties) {
			symbolsProperties.remove(oldSymbolName);
			symbolsProperties.put(symbolName, symbolValue);
		}
		symbolsUpdated();
	}

	public void symbolsDelete(String symbolName) {
		synchronized (symbolsProperties) {
			symbolsProperties.remove(symbolName);
		}
		symbolsUpdated();
	}

	public void symbolsDeleteAll() {
		synchronized (symbolsProperties) {
			symbolsProperties.clear();
		}
		symbolsUpdated();
	}
	
	public boolean symbolsProjectCheckUndefined(String projectName) throws Exception {
		final Project project = getOriginalProjectByName(projectName, false);
		if (project == null) {
			return false;
		}
		if (project.undefinedGlobalSymbols) {
			project.undefinedGlobalSymbols = false;
			new WalkHelper() {
			
				@Override
				protected void walk(DatabaseObject databaseObject) throws Exception {
					if (databaseObject.isSymbolError()) {
						project.undefinedGlobalSymbols = true;
					} else {
						super.walk(databaseObject);
					}
				}
				
			}.init(project);
		}
		return project.undefinedGlobalSymbols;
	}
	
	public Set<String> symbolsGetUndefined(String projectName) throws Exception {
		Project project = getOriginalProjectByName(projectName, false);
		final Set<String> allUndefinedSymbols = new HashSet<String>();
		
		if (project.undefinedGlobalSymbols) {
			new WalkHelper() {
				
				@Override
				protected void walk(DatabaseObject databaseObject) throws Exception {
					if (databaseObject.isSymbolError()) {
						for (Set<String> undefinedSymbols : databaseObject.getSymbolsErrors().values()) {
							allUndefinedSymbols.addAll(undefinedSymbols);
						}
					}
					super.walk(databaseObject);
				}
				
			}.init(project);
		}
		
		return Collections.unmodifiableSet(allUndefinedSymbols);
	}
	
	public void symbolsCreateUndefined(String projectName) throws Exception {
		Set<String> undefinedSymbols = symbolsGetUndefined(projectName);
		symbolsCreateUndefined(undefinedSymbols);
		Engine.logDatabaseObjectManager.info("The undefined global symbols for the project \"" + projectName + "\" are declared: " + undefinedSymbols);
	}

	public Set<String> symbolsSetCheckUndefined(Set<String> value) {
		for (String name : value) {
			if (symbolsProperties.containsKey(name)) {
				value = new HashSet<String>(value);
				value.removeAll(symbolsProperties.keySet());
				return Collections.unmodifiableSet(value);
			}
		}
		return value;
	}
	
	public void fullsyncUpdate() {
		for (Project project : projects.values()) {
			CouchDbManager.syncDocument(project);
		}
	}
	
	public boolean canOpenProject(String projectName) {
		return studioProjects.canOpen(projectName);
	}
	
	public DatabaseObject getDatabaseObjectByQName(String qname) throws Exception {
		String[] name = qname.split("\\.");
		String project = name[0];
		DatabaseObject dbo = getOriginalProjectByName(project);
		for (int i = 1; i < name.length; i++) {
			dbo = dbo.getDatabaseObjectChild(name[i]);
		}
		return dbo;
	}
}