

const NID_TEMPLATE = '<div class="ui-grid-cell-contents" title="{{ COL_FIELD }}">{{ COL_FIELD | limitTo:8 }}</div>';

const COLUMNS_DEFS = [
    {displayName: 'Node ID8', field: 'nid', headerTooltip: 'Node ID8', cellTemplate: NID_TEMPLATE, minWidth: 85, width: 145, pinnedLeft: true},
    {displayName: 'Node IP', field: 'ip', headerTooltip: 'Primary IP address of node', minWidth: 100, width: 150},
    {displayName: 'Grid name', field: 'gridName', headerTooltip: 'Name of node grid cluster', minWidth: 110, width: 150},
    {displayName: 'Version', field: 'version', headerTooltip: 'Node version', minWidth: 75, width: 140},
    {displayName: 'OS information', field: 'os', headerTooltip: 'OS information for node\'s host', minWidth: 125}
];

export default function controller($scope, $animate, uiGridConstants, nodes, options) {
    const $ctrl = this;

    const updateSelected = () => {
        const nids = $ctrl.gridApi.selection.legacyGetSelectedRows().map((node) => node.nid).sort();

        if (!_.isEqual(nids, $ctrl.selected))
            $ctrl.selected = nids;
    };

    $ctrl.nodes = nodes;
    $ctrl.options = options;
    $ctrl.selected = [];

    $ctrl.gridOptions = {
        data: nodes,
        columnVirtualizationThreshold: 30,
        columnDefs: COLUMNS_DEFS,
        enableRowSelection: true,
        enableRowHeaderSelection: false,
        enableColumnMenus: false,
        multiSelect: true,
        modifierKeysToMultiSelect: true,
        noUnselect: false,
        flatEntityAccess: true,
        fastWatch: true,
        onRegisterApi: (api) => {
            $animate.enabled(api.grid.element, false);

            $ctrl.gridApi = api;

            api.selection.on.rowSelectionChanged($scope, updateSelected);
            api.selection.on.rowSelectionChangedBatch($scope, updateSelected);

            $ctrl.gridApi.grid.element.css('height', '270px');

            setTimeout(() => $ctrl.gridApi.core.notifyDataChange(uiGridConstants.dataChange.COLUMN), 300);
        },
        ...options.grid
    };
}

controller.$inject = ['$scope', '$animate', 'uiGridConstants', 'nodes', 'options'];
