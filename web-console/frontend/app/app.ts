import angular from 'angular';

import './style.scss';

import './vendor';
import '../public/stylesheets/style.scss';
import '../app/primitives';

import './app.config';

import './modules/form/form.module';
import './modules/agent/agent.module';
import './modules/nodes/nodes.module';
import './modules/demo/Demo.module';

import './modules/states/logout.state';
import './modules/states/admin.state';
import './modules/states/errors.state';
import './modules/states/settings.state';

// ignite:modules
import './core';
import './modules/user/user.module';
import './modules/branding/branding.module';
import './modules/getting-started/GettingStarted.provider';
import './modules/ace.module';
import './modules/loading/loading.module';
import servicesModule from './services';
// endignite

// Data
import messagesEn from '../i18n/messages.en.json';
import messagesCn from '../i18n/messages.zh-CN.json';
import i18n from './data/i18n';
import i18nCn from './data/i18n-zh';

// Directives.
import igniteAutoFocus from './directives/auto-focus.directive';
import igniteCopyToClipboard from './directives/copy-to-clipboard.directive';
import igniteHideOnStateChange from './directives/hide-on-state-change/hide-on-state-change.directive';
import igniteMatch from './directives/match.directive';
import igniteOnClickFocus from './directives/on-click-focus.directive';
import igniteOnEnter from './directives/on-enter.directive';
import igniteOnEnterFocusMove from './directives/on-enter-focus-move.directive';
import igniteOnEscape from './directives/on-escape.directive';
import igniteOnFocusOut from './directives/on-focus-out.directive';
import igniteRetainSelection from './directives/retain-selection.directive';
import btnIgniteLink from './directives/btn-ignite-link';

// Services.
import ChartColors from './services/ChartColors.service';
import {default as IgniteConfirm, Confirm} from './services/Confirm.service';
import ConfirmBatch from './services/ConfirmBatch.service';
import CopyToClipboard from './services/CopyToClipboard.service';
import Countries from './services/Countries.service';
import ErrorPopover from './services/ErrorPopover.service';
import Focus from './services/Focus.service';
import FormUtils from './services/FormUtils.service';
import InetAddress from './services/InetAddress.service';
import JavaTypes from './services/JavaTypes.service';
import SqlTypes from './services/SqlTypes.service';
import LegacyUtils from './services/LegacyUtils.service';
import Messages from './services/Messages.service';
import ErrorParser from './services/ErrorParser.service';
import ModelNormalizer from './services/ModelNormalizer.service';
import {CSV} from './services/CSV';
import {$exceptionHandler} from './services/exceptionHandler';

import {Store} from './services/store';
import {UserService} from './modules/user/User.service';

import AngularStrapTooltip from './services/AngularStrapTooltip.decorator';
import AngularStrapSelect from './services/AngularStrapSelect.decorator';

// Filters.
import byName from './filters/byName.filter';
import bytes from './filters/bytes.filter';
import defaultName from './filters/default-name.filter';
import domainsValidation from './filters/domainsValidation.filter';
import duration from './filters/duration.filter';
import hasPojo from './filters/hasPojo.filter';
import uiGridSubcategories from './filters/uiGridSubcategories.filter';
import id8 from './filters/id8.filter';

// Components
import igniteListOfRegisteredUsers from './components/list-of-registered-users';
import dialogAdminCreateUser from './components/dialog-admin-create-user';
import IgniteActivitiesUserDialog from './components/activities-user-dialog';
import './components/input-dialog';
import webConsoleHeader from './components/web-console-header';
import webConsoleFooter from './components/web-console-footer';
import igniteIcon from './components/ignite-icon';
import versionPicker from './components/version-picker';
import userNotifications from './components/user-notifications';
import pageAdmin from './components/page-admin';
import pageQueries from './components/page-queries';
import gridColumnSelector from './components/grid-column-selector';
import gridItemSelected from './components/grid-item-selected';
import gridNoData from './components/grid-no-data';
import gridExport from './components/grid-export';
import gridShowingRows from './components/grid-showing-rows';
import bsSelectMenu from './components/bs-select-menu';
import protectFromBsSelectRender from './components/protect-from-bs-select-render';
import uiGrid from './components/ui-grid';
import uiGridHovering from './components/ui-grid-hovering';
import uiGridFilters from './components/ui-grid-filters';
import uiGridColumnResizer from './components/ui-grid-column-resizer';
import listEditable from './components/list-editable';
import breadcrumbs from './components/breadcrumbs';
import panelCollapsible from './components/panel-collapsible';
import clusterSelector from './components/cluster-selector';
import appDrawer from './components/app-drawer';
import connectedClusters from './components/connected-clusters-badge';
import connectedClustersDialog from './components/connected-clusters-dialog';
import stacktraceViewerDialog from './components/stacktrace-viewer-dialog';
import pageLanding from './components/page-landing';
import passwordVisibility from './components/password-visibility';
import progressLine from './components/progress-line';
import formField from './components/form-field';
import igniteChart from './components/ignite-chart';
import igniteChartSelector from './components/ignite-chart-series-selector';
import statusOutput from './components/status-output';
import timedRedirection from './components/timed-redirection';

import pagePasswordChanged from './components/page-password-changed';
import pagePasswordReset from './components/page-password-reset';
import pageSignup from './components/page-signup';
import pageSignin from './components/page-signin';
import pageForgotPassword from './components/page-forgot-password';
import formSignup from './components/form-signup';
import sidebar from './components/web-console-sidebar';
import permanentNotifications from './components/permanent-notifications';
import signupConfirmation from './components/page-signup-confirmation';
import noDataCmp from './components/no-data';
import globalProgressBar from './components/global-progress-line';

import igniteServices from './services';

import baseTemplate from 'views/base.pug';
import * as icons from '../public/images/icons';

import uiRouter from '@uirouter/angularjs';
import {upgradeModule} from '@uirouter/angular-hybrid';

export default angular
    .module('ignite-console', [
        // Optional AngularJS modules.
        'ngAnimate',
        'ngSanitize',
        'ngMessages',
        // Third party libs.
        'asyncFilter',
        'dndLists',
        'gridster',
        'mgcrea.ngStrap',
        'nvd3',
        'pascalprecht.translate',
        'smart-table',
        'treeControl',
        'ui.grid',
        'ui.grid.autoResize',
        'ui.grid.exporter',
        'ui.grid.resizeColumns',
        'ui.grid.saveState',
        'ui.grid.selection',
        uiRouter,
        upgradeModule.name,
        // Base modules.
        'ignite-console.core',
        'ignite-console.ace',
        'ignite-console.Form',
        'ignite-console.input-dialog',
        'ignite-console.user',
        'ignite-console.branding',
        'ignite-console.agent',
        'ignite-console.nodes',
        'ignite-console.demo',
        // States.
        'ignite-console.states.logout',
        'ignite-console.states.admin',
        'ignite-console.states.errors',
        'ignite-console.states.settings',
        // Common modules.
        'ignite-console.getting-started',
        'ignite-console.loading',
        // Ignite configuration module.
        'ignite-console.config',
        // Components
        webConsoleHeader.name,
        webConsoleFooter.name,
        igniteIcon.name,
        igniteServices.name,
        versionPicker.name,
        userNotifications.name,
        pageAdmin.name,
        pageQueries.name,
        gridColumnSelector.name,
        gridItemSelected.name,
        gridNoData.name,
        gridExport.name,
        gridShowingRows.name,
        bsSelectMenu.name,
        uiGrid.name,
        uiGridHovering.name,
        uiGridFilters.name,
        uiGridColumnResizer.name,
        protectFromBsSelectRender.name,
        AngularStrapTooltip.name,
        AngularStrapSelect.name,
        listEditable.name,
        panelCollapsible.name,
        clusterSelector.name,
        appDrawer.name,
        servicesModule.name,
        connectedClusters.name,
        connectedClustersDialog.name,
        stacktraceViewerDialog.name,
        igniteListOfRegisteredUsers.name,
        dialogAdminCreateUser.name,
        pageLanding.name,
        pagePasswordChanged.name,
        pagePasswordReset.name,
        pageSignup.name,
        pageSignin.name,
        pageForgotPassword.name,
        breadcrumbs.name,
        passwordVisibility.name,
        igniteChart.name,
        igniteChartSelector.name,
        statusOutput.name,
        progressLine.name,
        formField.name,
        formSignup.name,
        timedRedirection.name,
        sidebar.name,
        permanentNotifications.name,
        timedRedirection.name,
        signupConfirmation.name,
        noDataCmp.name,
        globalProgressBar.name
    ])
    // Routing should wait until Angular loads. Angular app part will start it back using serviceBootstrap component.
    .config(['$urlServiceProvider', ($urlService) => $urlService.deferIntercept()])
    .service('$exceptionHandler', $exceptionHandler)
    // Directives.
    .directive('igniteAutoFocus', igniteAutoFocus)
    .directive('igniteCopyToClipboard', igniteCopyToClipboard)
    .directive('hideOnStateChange', igniteHideOnStateChange)
    .directive('igniteMatch', igniteMatch)
    .directive('igniteOnClickFocus', igniteOnClickFocus)
    .directive('igniteOnEnter', igniteOnEnter)
    .directive('igniteOnEnterFocusMove', igniteOnEnterFocusMove)
    .directive('igniteOnEscape', igniteOnEscape)
    .directive('igniteRetainSelection', igniteRetainSelection)
    .directive('igniteOnFocusOut', igniteOnFocusOut)
    .directive('btnIgniteLinkDashedSuccess', btnIgniteLink)
    .directive('btnIgniteLinkDashedSecondary', btnIgniteLink)
    // Services.
    .service('IgniteErrorPopover', ErrorPopover)
    .service('JavaTypes', JavaTypes)
    .service('SqlTypes', SqlTypes)
    .service('IgniteChartColors', ChartColors)
    .service('IgniteConfirm', IgniteConfirm)
    .service('Confirm', Confirm)
    .service('IgniteConfirmBatch', ConfirmBatch)
    .service('IgniteCopyToClipboard', CopyToClipboard)
    .service('IgniteCountries', Countries)
    .service('IgniteFocus', Focus)
    .service('IgniteInetAddress', InetAddress)
    .service('IgniteMessages', Messages)
    .service('IgniteErrorParser', ErrorParser)
    .service('IgniteModelNormalizer', ModelNormalizer)
    .service('IgniteFormUtils', FormUtils)
    .service('IgniteLegacyUtils', LegacyUtils)
    .service('IgniteActivitiesUserDialog', IgniteActivitiesUserDialog)
    .service('CSV', CSV)
    .service('Store', Store)
    // Filters.
    .filter('byName', byName)
    .filter('bytes', bytes)
    .filter('defaultName', defaultName)
    .filter('domainsValidation', domainsValidation)
    .filter('duration', duration)
    .filter('hasPojo', hasPojo)
    .filter('uiGridSubcategories', uiGridSubcategories)
    .filter('id8', id8)
    .config(['$translateProvider', '$stateProvider', '$locationProvider', '$urlRouterProvider',
        /**
         * @param {angular.translate.ITranslateProvider} $translateProvider
         * @param {import('@uirouter/angularjs').StateProvider} $stateProvider
         * @param {ng.ILocationProvider} $locationProvider
         * @param {import('@uirouter/angularjs').UrlRouterProvider} $urlRouterProvider
         */
        ($translateProvider, $stateProvider, $locationProvider, $urlRouterProvider) => {
            $translateProvider.translations('en', messagesEn);
            $translateProvider.translations('en', i18n);            
            
            $translateProvider.translations('zh-CN', messagesCn);
            $translateProvider.translations('zh-CN', i18nCn);            
            
            $translateProvider.preferredLanguage('zh-CN');

            // Set up the states.
            $stateProvider
                .state('base', {
                    url: '',
                    abstract: true,
                    template: baseTemplate
                });

            $urlRouterProvider.otherwise('/404');
            $locationProvider.html5Mode(true);
        }])
    .run(['User', 'AgentManager',
        /**
         * @param {import('./modules/agent/AgentManager.service').default} agentMgr
         */
        (User: UserService, agentMgr) => {
            let lastUser;

            User.current$.subscribe((user) => {
                if (lastUser)
                    return;

                lastUser = user;

                agentMgr.connect();
            });
        }
    ])
    .run(['$transitions',
        /**
         * @param {import('@uirouter/angularjs').TransitionService} $transitions
         */
        ($transitions) => {
            $transitions.onSuccess({ }, (trans) => {
                try {
                    const {name, unsaved} = trans.$to();
                    const params = trans.params();

                    if (unsaved)
                        localStorage.removeItem('lastStateChangeSuccess');
                    else
                        localStorage.setItem('lastStateChangeSuccess', JSON.stringify({name, params}));
                }
                catch (ignored) {
                    // No-op.
                }
            });
        }
    ])
    .run(['IgniteIcon',
        /**
         * @param {import('./components/ignite-icon/service').default} IgniteIcon
         */
        (IgniteIcon) => IgniteIcon.registerIcons(icons)
    ]);
