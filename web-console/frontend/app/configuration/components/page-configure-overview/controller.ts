

import {Subject, Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import naturalCompare from 'natural-compare-lite';

const cellTemplate = (state) => `
    <div class="ui-grid-cell-contents">
        <a
            class="link-success"
            ui-sref="${state}({clusterID: row.entity.id})"
            title='Click to edit'
        >{{ row.entity[col.field] }}</a>
    </div>
`;


import {default as ConfigureState} from '../../services/ConfigureState';
import {default as ConfigSelectors} from '../../store/selectors';
import {default as Clusters} from '../../services/Clusters';
import {default as ModalPreviewProject} from '../../components/modal-preview-project/service';
import {default as ConfigurationDownload} from '../../services/ConfigurationDownload';

import {confirmClustersRemoval} from '../../store/actionCreators';

import {UIRouter} from '@uirouter/angularjs';
import {ShortCluster} from '../../types';
import {IColumnDefOf} from 'ui-grid';

export default class PageConfigureOverviewController {
    static $inject = [
        '$uiRouter',
        'ModalPreviewProject',
        'Clusters',
        'ConfigureState',
        'ConfigSelectors',
        'ConfigurationDownload'
    ];

    constructor(
        private $uiRouter: UIRouter,
        private ModalPreviewProject: ModalPreviewProject,
        private Clusters: Clusters,
        private ConfigureState: ConfigureState,
        private ConfigSelectors: ConfigSelectors,
        private ConfigurationDownload: ConfigurationDownload
    ) {}

    shortClusters$: Observable<Array<ShortCluster>>;
    clustersColumnDefs: Array<IColumnDefOf<ShortCluster>>;
    selectedRows$: Subject<Array<ShortCluster>>;
    selectedRowsIDs$: Observable<Array<string>>;

    $onDestroy() {
        this.selectedRows$.complete();
    }

    removeClusters(clusters: Array<ShortCluster>) {
        this.ConfigureState.dispatchAction(confirmClustersRemoval(clusters.map((c) => c.id)));

        // TODO: Implement storing selected rows in store to share this data between other components.
        this.selectedRows$.next([]);
    }

    editCluster(cluster: ShortCluster) {
        return this.$uiRouter.stateService.go('^.edit', {clusterID: cluster.id});
    }

    $onInit() {
        this.shortClusters$ = this.ConfigureState.state$.pipe(this.ConfigSelectors.selectShortClustersValue());

        this.clustersColumnDefs = [
            {
                name: 'name',
                displayName: 'Name',
                field: 'name',
                enableHiding: false,
                filter: {
                    placeholder: 'Filter by name…'
                },
                sort: {direction: 'asc', priority: 0},
                sortingAlgorithm: naturalCompare,
                cellTemplate: cellTemplate('base.configuration.edit'),
                width: 200
            },
            {
                name: 'comment',
                displayName: 'Comment',
                field: 'comment',
                enableHiding: false,
                filter: {
                    placeholder: 'Filter by comment…'
                },
                sort: {direction: 'asc', priority: 0},
                sortingAlgorithm: naturalCompare,
                cellTemplate: cellTemplate('base.configuration.edit.basic'),
                minWidth: 200
            },
            {
                name: 'discovery',
                displayName: 'Discovery',
                field: 'discovery',
                multiselectFilterOptions: this.Clusters.discoveries,
                width: 200
            },
            {
                name: 'caches',
                displayName: 'Caches',
                field: 'cachesCount',
                cellClass: 'ui-grid-number-cell',
                cellTemplate: cellTemplate('base.configuration.edit.advanced.caches'),
                enableFiltering: false,
                type: 'number',
                width: 95
            },
            {
                name: 'models',
                displayName: 'Models',
                field: 'modelsCount',
                cellClass: 'ui-grid-number-cell',
                cellTemplate: cellTemplate('base.configuration.edit.advanced.models'),
                enableFiltering: false,
                type: 'number',
                width: 95
            },
            {
                name: 'status',
                displayName: 'Status',
                field: 'status',
                cellClass: 'ui-grid-number-cell',
                cellTemplate: cellTemplate('base.configuration.edit.basic.models'),
                enableFiltering: false,
                type: 'string',
                width: 95
            }
        ];

        this.selectedRows$ = new Subject();

        this.selectedRowsIDs$ = this.selectedRows$.pipe(map((selectedClusters) => selectedClusters.map((cluster) => cluster.id)));

        this.actions$ = this.selectedRows$.pipe(map((selectedClusters) => [
            {
                action: 'Edit',
                click: () => this.editCluster(selectedClusters[0]),
                available: selectedClusters.length === 1
            },
            {
                action: 'See project structure',
                click: () => this.ModalPreviewProject.open(selectedClusters[0]),
                available: selectedClusters.length === 1
            },
            {
                action: 'Download project',
                click: () => this.ConfigurationDownload.downloadClusterConfiguration(selectedClusters[0]),
                available: selectedClusters.length === 1
            },
            {
                action: 'Delete',
                click: () => this.removeClusters(selectedClusters),
                available: true
            }
        ]));
    }
}
