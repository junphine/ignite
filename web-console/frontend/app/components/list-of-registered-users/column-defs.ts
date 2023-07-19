

const ICON_SORT = '<span ui-grid-one-bind-id-grid="col.uid + \'-sortdir-text\'" ui-grid-visible="col.sort.direction" aria-label="Sort Descending"><i ng-class="{ \'ui-grid-icon-up-dir\': col.sort.direction == asc, \'ui-grid-icon-down-dir\': col.sort.direction == desc, \'ui-grid-icon-blank\': !col.sort.direction }" title="" aria-hidden="true"></i></span>';

const USER_TEMPLATE = '<div class="ui-grid-cell-contents user-cell">' +
    '<i class="pull-left" ng-class="row.entity.admin ? \'icon-admin\' : \'icon-user\'"></i>&nbsp;<label bs-tooltip data-title="{{ COL_FIELD }}">{{ COL_FIELD }}</label></div>';

const CLUSTER_HEADER_TEMPLATE = `<div class='ui-grid-cell-contents' bs-tooltip data-title='{{ col.headerTooltip(col) }}' data-placement='top'><i class='icon-cluster'></i>${ICON_SORT}</div>`;
const MODEL_HEADER_TEMPLATE = `<div class='ui-grid-cell-contents' bs-tooltip data-title='{{ col.headerTooltip(col) }}' data-placement='top'><i class='fa fa-object-group'></i>${ICON_SORT}</div>`;
const CACHE_HEADER_TEMPLATE = `<div class='ui-grid-cell-contents' bs-tooltip data-title='{{ col.headerTooltip(col) }}' data-placement='top'><i class='fa fa-database'></i>${ICON_SORT}</div>`;

const EMAIL_TEMPLATE = '<div class="ui-grid-cell-contents"><a bs-tooltip data-title="{{ COL_FIELD }}" ng-href="mailto:{{ COL_FIELD }}">{{ COL_FIELD }}</a></div>';
const DATE_WITH_TITLE = '<div class="ui-grid-cell-contents"><label bs-tooltip data-title="{{ COL_FIELD | date:\'M/d/yy HH:mm\' }}">{{ COL_FIELD | date:"M/d/yy HH:mm" }}</label></div>';
const VALUE_WITH_TITLE = '<div class="ui-grid-cell-contents"><label bs-tooltip data-title="{{ COL_FIELD }}">{{ COL_FIELD }}</label></div>';

export const columnDefsFn = ($translate: ng.translate.ITranslateService) => [
    {name: 'user', enableHiding: false, displayName: $translate.instant('admin.listOfRegisteredUsers.columns.user.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.user'), field: 'userName', cellTemplate: USER_TEMPLATE, minWidth: 160, enableFiltering: true, pinnedLeft: true, filter: { placeholder: 'Filter by name...' }},
    {name: 'email', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.email.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.email'), field: 'email', cellTemplate: EMAIL_TEMPLATE, minWidth: 160, width: 220, enableFiltering: true, filter: { placeholder: 'Filter by email...' }},
    {name: 'activated', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.activated.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.activated'), field: 'activated', width: 220, enableFiltering: true, filter: { placeholder: 'Filter by activation...' }, visible: false},
    {name: 'company', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.company.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.company'), field: 'company', cellTemplate: VALUE_WITH_TITLE, minWidth: 180, enableFiltering: true, filter: { placeholder: 'Filter by company...' }},
    {name: 'country', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.country.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.country'), field: 'countryCode', cellTemplate: VALUE_WITH_TITLE, minWidth: 160, enableFiltering: true, filter: { placeholder: 'Filter by country...' }},
    {name: 'lastlogin', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.lastlogin.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.lastLogin'), field: 'lastLogin', cellTemplate: DATE_WITH_TITLE, minWidth: 135, width: 135, enableFiltering: false, visible: false},
    {name: 'lastactivity', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.lastactivity.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.lastActivity'), field: 'lastActivity', cellTemplate: DATE_WITH_TITLE, minWidth: 135, width: 145, enableFiltering: false, visible: true, sort: { direction: 'desc', priority: 0 }},
    // Configurations
    {name: 'cfg_clusters', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.cfg_clusters.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurations'), headerCellTemplate: CLUSTER_HEADER_TEMPLATE, field: 'counters.clusters', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Clusters count', minWidth: 65, width: 65, enableFiltering: false, visible: false},
    {name: 'cfg_models', displayName: 'Models count', categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurations'), headerCellTemplate: MODEL_HEADER_TEMPLATE, field: 'counters.models', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Models count', minWidth: 65, width: 65, enableFiltering: false, visible: false},
    {name: 'cfg_caches', displayName: 'Caches count', categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurations'), headerCellTemplate: CACHE_HEADER_TEMPLATE, field: 'counters.caches', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Caches count', minWidth: 65, width: 65, enableFiltering: false, visible: false},
    // Activities Total
    {name: 'cfg', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.cfg.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.totalActivities'), field: 'activitiesTotal["configuration"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Total count of configuration usages', minWidth: 70, width: 70, enableFiltering: false},
    {name: 'qry', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.qry.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.totalActivities'), field: 'activitiesTotal["queries"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Total count of queries usages', minWidth: 70, width: 70, enableFiltering: false},
    {name: 'demo', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.demo.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.totalActivities'), field: 'activitiesTotal["demo"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Total count of demo startup', minWidth: 85, width: 85, enableFiltering: false},
    {name: 'dnld', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.dnld.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.totalActivities'), field: 'activitiesDetail["/agent/download"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Total count of agent downloads', minWidth: 80, width: 80, enableFiltering: false},
    {name: 'starts', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.starts.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.totalActivities'), field: 'activitiesDetail["/agent/start"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Total count of agent startup', minWidth: 87, width: 87, enableFiltering: false},
    // Activities Configuration
    {name: 'clusters', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusters.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.overview"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 100, enableFiltering: false, visible: false},
    {name: 'clusterBasic', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterBasic.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.basic"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 100, enableFiltering: false, visible: false},
    {name: 'clusterBasicNew', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterBasicNew.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["/configuration/new/basic"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedNew', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedNew.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["/configuration/new/advanced/cluster"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 170, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedCluster', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedCluster.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.advanced.cluster"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedCaches', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedCaches.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.advanced.caches"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedCache', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedCache.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.advanced.caches.cache"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedModels', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedModels.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.advanced.models"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    {name: 'clusterAdvancedModel', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.clusterAdvancedModel.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.configurationActivities'), field: 'activitiesDetail["base.configuration.edit.advanced.models.model"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Configuration clusters', minWidth: 100, width: 150, enableFiltering: false, visible: false},
    // Activities Queries
    {name: 'execute', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.execute.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.queriesActivities'), field: 'activitiesDetail["/queries/execute"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Query executions', minWidth: 98, width: 98, enableFiltering: false, visible: false},
    {name: 'explain', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.explain.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.queriesActivities'), field: 'activitiesDetail["/queries/explain"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Query explain executions', minWidth: 95, width: 95, enableFiltering: false, visible: false},
    {name: 'scan', displayName: $translate.instant('admin.listOfRegisteredUsers.columns.scan.title'), categoryDisplayName: $translate.instant('admin.listOfRegisteredUsers.categoryDisplayNames.queriesActivities'), field: 'activitiesDetail["/queries/scan"] || 0', cellTemplate: VALUE_WITH_TITLE, type: 'number', cellClass: 'ui-grid-number-cell', headerTooltip: 'Scan query executions', minWidth: 80, width: 80, enableFiltering: false, visible: false}
];
