import angular from 'angular';
import uiValidate from 'angular-ui-validate';
import {UIRouterRx} from '@uirouter/rx';
import {UIRouter} from '@uirouter/angularjs';

import {withLatestFrom, tap, filter, scan} from 'rxjs/operators';

import generatorModule from './generator/configuration.module';

import ConfigureState from './services/ConfigureState';
import PageConfigure from './services/PageConfigure';
import ConfigurationDownload from './services/ConfigurationDownload';
import ConfigChangesGuard from './services/ConfigChangesGuard';
import ConfigSelectionManager from './services/ConfigSelectionManager';
import SummaryZipper from './services/SummaryZipper';
import ConfigurationResource from './services/ConfigurationResource';
import selectors from './store/selectors';
import effects from './store/effects';
import Clusters from './services/Clusters';
import Caches from './services/Caches';
import Models from './services/Models';
import IGFSs from './services/IGFSs';

import pageConfigure from './components/page-configure';
import pageConfigureBasic from './components/page-configure-basic';
import pageConfigureAdvanced from './components/page-configure-advanced';
import pageConfigureCrudUI from './components/page-configure-crudui';
import pageConfigureOverview from './components/page-configure-overview';
import Datasource from 'app/datasource/services/Datasource';

import projectStructurePreview from './components/modal-preview-project';
import pcItemsTable from './components/pc-items-table';
import pcUiGridFilters from './components/pc-ui-grid-filters';
import isInCollection from './components/pcIsInCollection';
import pcValidation from './components/pcValidation';
import fakeUiCanExit from './components/fakeUICanExit';
import formUICanExitGuard from './components/formUICanExitGuard';
import modalImportModels from './components/modal-import-models';
import buttonImportModels from './components/button-import-models';
import buttonDownloadProject from './components/button-download-project';
import buttonPreviewProject from './components/button-preview-project';
import previewPanel from './components/preview-panel';
import pcSplitButton from './components/pc-split-button';
import uiAceTabs from './components/ui-ace-tabs.directive';

import uiAceJava from './components/ui-ace-java';
import uiAceSpring from './components/ui-ace-spring';

import {registerStates} from './states';

import {
    editReducer2,
    shortObjectsReducer,
    editReducer,
    loadingReducer,
    itemsEditReducerFactory,
    mapStoreReducerFactory,
    mapCacheReducerFactory,
    basicCachesActionTypes,
    clustersActionTypes,
    shortClustersActionTypes,
    cachesActionTypes,
    shortCachesActionTypes,
    modelsActionTypes,
    shortModelsActionTypes,
    igfssActionTypes,
    shortIGFSsActionTypes,
    refsReducer
} from './store/reducer';

import {errorState} from './transitionHooks/errorState';
import {default as ActivitiesData} from '../core/activities/Activities.data';

registerActivitiesHook.$inject = ['$uiRouter', 'IgniteActivitiesData'];

function registerActivitiesHook($uiRouter: UIRouter, ActivitiesData: ActivitiesData) {
    $uiRouter.transitionService.onSuccess({to: 'base.configuration.**'}, (transition) => {
        ActivitiesData.post({group: 'configuration', action: transition.targetState().name()});
    });
}

export default angular.module('ignite-console.configuration', [
        uiValidate,
        'asyncFilter',
        generatorModule.name,
        pageConfigure.name,
        pageConfigureBasic.name,
        pageConfigureAdvanced.name,
        pageConfigureCrudUI.name,
        pageConfigureOverview.name,
        pcUiGridFilters.name,
        projectStructurePreview.name,        
        modalImportModels.name,
        buttonImportModels.name,
        buttonDownloadProject.name,
        buttonPreviewProject.name,
        previewPanel.name,
        
        pcSplitButton.name,
        pcItemsTable.name,
        pcValidation.name,
        uiAceJava.name,
        uiAceSpring.name
    ])
    .config(registerStates)
    .run(registerActivitiesHook)
    .run(errorState)
    .run(['ConfigEffects', 'ConfigureState', '$uiRouter', (ConfigEffects, ConfigureState, $uiRouter) => {
        $uiRouter.plugin(UIRouterRx);

        ConfigureState.addReducer(refsReducer({
            models: {at: 'domains', store: 'caches'},
            caches: {at: 'caches', store: 'models'}
        }));

        ConfigureState.addReducer((state, action) => Object.assign({}, state, {
            clusterConfiguration: editReducer(state.clusterConfiguration, action),
            configurationLoading: loadingReducer(state.configurationLoading, action),
            basicCaches: itemsEditReducerFactory(basicCachesActionTypes)(state.basicCaches, action),
            clusters: mapStoreReducerFactory(clustersActionTypes)(state.clusters, action),
            shortClusters: mapCacheReducerFactory(shortClustersActionTypes)(state.shortClusters, action),
            caches: mapStoreReducerFactory(cachesActionTypes)(state.caches, action),
            shortCaches: mapCacheReducerFactory(shortCachesActionTypes)(state.shortCaches, action),
            models: mapStoreReducerFactory(modelsActionTypes)(state.models, action),
            shortModels: mapCacheReducerFactory(shortModelsActionTypes)(state.shortModels, action),
            igfss: mapStoreReducerFactory(igfssActionTypes)(state.igfss, action),
            shortIgfss: mapCacheReducerFactory(shortIGFSsActionTypes)(state.shortIgfss, action),
            edit: editReducer2(state.edit, action)
        }));

        ConfigureState.addReducer(shortObjectsReducer);

        ConfigureState.addReducer((state, action) => {
            switch (action.type) {
                case 'APPLY_ACTIONS_UNDO':
                    return action.state;

                default:
                    return state;
            }
        });

        const la = ConfigureState.actions$.pipe(scan((acc, action) => [...acc, action], []));

        ConfigureState.actions$.pipe(
            filter((a) => a.type === 'UNDO_ACTIONS'),
            withLatestFrom(la, ({actions}, actionsWindow, initialState) => {
                return {
                    type: 'APPLY_ACTIONS_UNDO',
                    state: actionsWindow.filter((a) => !actions.includes(a)).reduce(ConfigureState._combinedReducer, {})
                };
            }),
            tap((a) => ConfigureState.dispatchAction(a))
        )
        .subscribe();
        ConfigEffects.connect();
    }])
    .factory('configSelectionManager', ConfigSelectionManager)
    .service('IgniteSummaryZipper', SummaryZipper)
    .service('IgniteConfigurationResource', ConfigurationResource)
    .service('ConfigSelectors', selectors)
    .service('ConfigEffects', effects)
    .service('ConfigChangesGuard', ConfigChangesGuard)
    .service('PageConfigure', PageConfigure)
    .service('ConfigureState', ConfigureState)
    .service('ConfigurationDownload', ConfigurationDownload)
    .service('Clusters', Clusters)
    .service('Caches', Caches)
    .service('Models', Models)
    .service('IGFSs', IGFSs)
    .service('Datasource', Datasource)
    .directive('pcIsInCollection', isInCollection)
    .directive('fakeUiCanExit', fakeUiCanExit)
    .directive('formUiCanExitGuard', formUICanExitGuard)
    .directive('igniteUiAceTabs', uiAceTabs)
    ;
