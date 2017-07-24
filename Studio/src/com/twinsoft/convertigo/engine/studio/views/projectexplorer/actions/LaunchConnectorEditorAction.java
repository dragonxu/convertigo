package com.twinsoft.convertigo.engine.studio.views.projectexplorer.actions;

import com.twinsoft.convertigo.engine.studio.AbstractRunnableAction;
import com.twinsoft.convertigo.engine.studio.views.projectexplorer.WrapStudio;
import com.twinsoft.convertigo.engine.studio.views.projectexplorer.model.ConnectorView;
import com.twinsoft.convertigo.engine.studio.views.projectexplorer.model.WrapDatabaseObject;

public class LaunchConnectorEditorAction extends AbstractRunnableAction {

    public LaunchConnectorEditorAction(WrapStudio studio) {
        super(studio);
    }

    @Override
    protected void run2() {
        WrapDatabaseObject treeObject = (WrapDatabaseObject) studio.getFirstSelectedTreeObject();
        ((ConnectorView) treeObject).launchEditor();
    }
}