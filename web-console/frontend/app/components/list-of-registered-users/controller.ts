

import _ from 'lodash';

import {columnDefsFn} from './column-defs';
import {categoriesFn} from './categories';
import {UserService} from 'app/modules/user/User.service';
import {Subscription} from 'rxjs';
import {tap} from 'rxjs/operators';

import headerTemplate from 'app/primitives/ui-grid-header/index.tpl.pug';

const rowTemplate = `<div
  ng-repeat="(colRenderIndex, col) in colContainer.renderedColumns track by col.uid"
  ui-grid-one-bind-id-grid="rowRenderIndex + '-' + col.uid + '-cell'"
  class="ui-grid-cell"
  ng-class="{ 'ui-grid-row-header-cell': col.isRowHeader }"
  role="{{col.isRowHeader ? 'rowheader' : 'gridcell'}}"
  ui-grid-cell/>`;

const treeAggregationFinalizerFn = function(agg) {
    return agg.rendered = agg.value;
};

export default class IgniteListOfRegisteredUsersCtrl {
    gridOptions: uiGrid.IGridOptions;
    gridApi: uiGrid.IGridApi;

    static $inject = ['$scope', '$state', '$filter', 'User', 'uiGridGroupingConstants', 'uiGridPinningConstants', 'IgniteAdminData', 'IgniteNotebookData', 'IgniteConfirm', 'IgniteActivitiesUserDialog', '$translate'];

    constructor(
        $scope,
        private $state,
        $filter,
        private User: UserService,
        uiGridGroupingConstants,
        uiGridPinningConstants,
        private AdminData,
        private NotebookData,
        private Confirm,
        private ActivitiesUserDialog,
        public $translate: ng.translate.ITranslateService
    ) {
        this.NotebookData = NotebookData;

        const dtFilter = $filter('date');

        this.groupBy = 'user';

        this.selected = [];

        this.params = {
            startDate: new Date(),
            endDate: new Date()
        };

        this.uiGridPinningConstants = uiGridPinningConstants;
        this.uiGridGroupingConstants = uiGridGroupingConstants;

        User.read().then((user) => this.user = user);

        const companiesExcludeFilter = (renderableRows) => {
            if (_.isNil(this.params.companiesExclude))
                return renderableRows;

            _.forEach(renderableRows, (row) => {
                row.visible = _.isEmpty(this.params.companiesExclude) ||
                    row.entity.company.toLowerCase().indexOf(this.params.companiesExclude.toLowerCase()) === -1;
            });

            return renderableRows;
        };

        this.actionOptions = [
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.becomeThisUser'),
                click: () => this.becomeUser(),
                available: true
            },
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.removeAdmin'),
                click: () => this.toggleAdmin(),
                available: true
            },
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.grantAdmin'),
                click: () => this.toggleAdmin(),
                available: false
            },
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.addUser'),
                sref: '.createUser',
                available: true
            },
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.removeUser'),
                click: () => this.removeUser(),
                available: true
            },
            {
                action: this.$translate.instant('admin.listOfRegisteredUsers.actions.activityDetail'),
                click: () => this.showActivities(),
                available: true
            }
        ];

        this.gridOptions = {
            data: [],

            columnDefs: this.getColumnDefs(),
            categories: this.getCategories(),

            treeRowHeaderAlwaysVisible: true,
            headerTemplate,
            columnVirtualizationThreshold: 30,
            rowTemplate,
            rowHeight: 46,
            suppressRemoveSort: false,
            enableFiltering: true,
            enableRowSelection: true,
            enableFullRowSelection: true,
            enableRowHeaderSelection: false,
            enableColumnMenus: false,
            multiSelect: false,
            modifierKeysToMultiSelect: true,
            noUnselect: false,
            fastWatch: true,
            exporterSuppressColumns: ['actions'],
            exporterCsvColumnSeparator: ';',
            rowIdentity: (row) => row.id,
            getRowIdentity: (row) => row.id,
            onRegisterApi: (api) => {
                this.gridApi = api;

                api.selection.on.rowSelectionChanged($scope, this._updateSelected.bind(this));
                api.selection.on.rowSelectionChangedBatch($scope, this._updateSelected.bind(this));

                api.core.on.filterChanged($scope, this._filteredRows.bind(this));
                api.core.on.rowsVisibleChanged($scope, this._filteredRows.bind(this));

                api.grid.registerRowsProcessor(companiesExcludeFilter, 50);

                $scope.$watch(() => this.gridApi.grid.getVisibleRows().length, (rows) => this.adjustHeight(rows));
                $scope.$watch(() => this.params.companiesExclude, () => this.gridApi.grid.refreshRows());
            }
        };

        /**
         * @param {{startDate: number, endDate: number}} params
         */
        const reloadUsers = (params) => {
            AdminData.loadUsers(params)
                .then((data) => {
                    this.gridOptions.data = data;

                    this.companies = _.values(_.groupBy(data, 'company'));
                    this.countries = _.values(_.groupBy(data, 'countryCode'));

                    this._refreshRows();
                });
        };

        const filterDates = _.debounce(() => {
            const sdt = this.params.startDate;
            const edt = this.params.endDate;

            this.exporterCsvFilename = `web_console_users_${dtFilter(sdt, 'yyyy_MM')}.csv`;

            const startDate = Date.UTC(sdt.getFullYear(), sdt.getMonth(), 1);
            const endDate = Date.UTC(edt.getFullYear(), edt.getMonth() + 1, 1);

            reloadUsers({ startDate, endDate });
        }, 250);

        this.subscriber = this.User.created$.pipe(tap(() => filterDates())).subscribe();
        $scope.$watch(() => this.params.startDate, filterDates);
        $scope.$watch(() => this.params.endDate, filterDates);
    }

    subscriber: Subscription;

    $onDestroy() {
        if (this.subscriber) this.subscriber.unsubscribe();
    }

    adjustHeight(rows) {
        // Add header height.
        const height = Math.min(rows, 11) * 48 + 78;

        this.gridApi.grid.element.css('height', height + 'px');

        this.gridApi.core.handleWindowResize();
    }

    _filteredRows() {
        const filtered = _.filter(this.gridApi.grid.rows, ({ visible}) => visible);

        this.filteredRows = _.map(filtered, 'entity');
    }

    _updateSelected() {
        const ids = this.gridApi.selection.legacyGetSelectedRows().map(({ id }) => id).sort();

        if (!_.isEqual(ids, this.selected))
            this.selected = ids;

        if (ids.length) {
            const user = this.gridApi.selection.legacyGetSelectedRows()[0];
            const other = this.user.email !== user.email;

            this.actionOptions[0].available = other; // Become this user.
            this.actionOptions[1].available = other && user.admin; // Revoke admin.
            this.actionOptions[2].available = other && !user.admin; // Grant admin.
            this.actionOptions[4].available = other; // Remove user.
            this.actionOptions[5].available = true; // Activity detail.
        }
        else {
            this.actionOptions[0].available = false; // Become this user.
            this.actionOptions[1].available = false; // Revoke admin.
            this.actionOptions[2].available = false; // Grant admin.
            this.actionOptions[4].available = false; // Remove user.
            this.actionOptions[5].available = false; // Activity detail.
        }
    }

    _refreshRows() {
        if (this.gridApi) {
            this.gridApi.grid.refreshRows()
                .then(() => this._updateSelected());
        }
    }

    becomeUser() {
        const user = this.gridApi.selection.legacyGetSelectedRows()[0];

        this.AdminData.becomeUser(user.email)
            .then(() => this.User.load())
            .then(() => this.$state.go('default-state'))
            .then(() => this.NotebookData.load());
    }

    toggleAdmin() {
        if (!this.gridApi)
            return;

        const user = this.gridApi.selection.legacyGetSelectedRows()[0];

        if (user.adminChanging)
            return;

        user.adminChanging = true;

        this.AdminData.toggleAdmin(user)
            .finally(() => {
                this._updateSelected();

                user.adminChanging = false;
            });
    }

    removeUser() {
        const user = this.gridApi.selection.legacyGetSelectedRows()[0];

        this.Confirm.confirm(this.$translate.instant('admin.listOfRegisteredUsers.removeUserConfirmationMessage', {userName: user.userName}))
            .then(() => this.AdminData.removeUser(user))
            .then(() => {
                const i = _.findIndex(this.gridOptions.data, (u) => u.id === user.id);

                if (i >= 0) {
                    this.gridOptions.data.splice(i, 1);
                    this.gridApi.selection.clearSelectedRows();
                }

                this.adjustHeight(this.gridOptions.data.length);

                return this._refreshRows();
            });
    }

    showActivities() {
        const user = this.gridApi.selection.legacyGetSelectedRows()[0];

        return new this.ActivitiesUserDialog({ user });
    }

    groupByUser() {
        this.groupBy = 'user';

        this.gridApi.grouping.clearGrouping();
        this.gridApi.selection.clearSelectedRows();

        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'company'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.NONE);
        });

        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'country'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.NONE);
        });

        this.gridOptions.categories = this.getCategories();
    }

    groupByCompany() {
        this.groupBy = 'company';

        this.gridApi.grouping.clearGrouping();
        this.gridApi.selection.clearSelectedRows();

        _.forEach(this.gridApi.grid.columns, (col) => {
            col.enableSorting = true;

            if (col.colDef.type !== 'number')
                return;

            this.gridApi.grouping.aggregateColumn(col.colDef.name, this.uiGridGroupingConstants.aggregation.SUM);
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.aggregateColumn('user', this.uiGridGroupingConstants.aggregation.COUNT);
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'user'}), (col) => {
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.aggregateColumn('lastactivity', this.uiGridGroupingConstants.aggregation.MAX);
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'lastactivity'}), (col) => {
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.groupColumn('company');
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'company'}), (col) => {
            col.customTreeAggregationFinalizerFn = (agg) => agg.rendered = agg.groupVal;
        });

        // Pinning left company.
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'company'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.LEFT);
        });

        // Unpinning country.
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'country'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.NONE);
        });

        const _categories = this.getCategories();
        // Cut company category.
        const company = _categories.splice(3, 1)[0];
        company.selectable = false;

        // Add company as first column.
        _categories.unshift(company);
        this.gridOptions.categories = _categories;
    }

    groupByCountry() {
        this.groupBy = 'country';

        this.gridApi.grouping.clearGrouping();
        this.gridApi.selection.clearSelectedRows();

        _.forEach(this.gridApi.grid.columns, (col) => {
            col.enableSorting = true;

            if (col.colDef.type !== 'number')
                return;

            this.gridApi.grouping.aggregateColumn(col.colDef.name, this.uiGridGroupingConstants.aggregation.SUM);
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.aggregateColumn('user', this.uiGridGroupingConstants.aggregation.COUNT);
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'user'}), (col) => {
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.aggregateColumn('lastactivity', this.uiGridGroupingConstants.aggregation.MAX);
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'lastactivity'}), (col) => {
            col.customTreeAggregationFinalizerFn = treeAggregationFinalizerFn;
        });

        this.gridApi.grouping.groupColumn('country');
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'country'}), (col) => {
            col.customTreeAggregationFinalizerFn = (agg) => agg.rendered = agg.groupVal;
        });

        // Pinning left country.
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'country'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.LEFT);
        });

        // Unpinning country.
        _.forEach(_.filter(this.gridApi.grid.columns, {name: 'company'}), (col) => {
            this.gridApi.pinning.pinColumn(col, this.uiGridPinningConstants.container.NONE);
        });

        const _categories = this.getCategories();
        // Cut company category.
        const country = _categories.splice(4, 1)[0];
        country.selectable = false;

        // Add company as first column.
        _categories.unshift(country);
        this.gridOptions.categories = _categories;
    }

    getColumnDefs() {
        return columnDefsFn(this.$translate);
    }

    getCategories() {
        return categoriesFn(this.$translate);
    }
}
