import cloneDeep from 'lodash/cloneDeep';
import uuidv4 from 'uuid/v4';
import {Subject, merge, combineLatest} from 'rxjs';
import {tap, map, take, refCount, pluck, publishReplay, switchMap, distinctUntilChanged} from 'rxjs/operators';
import {UIRouter, TransitionService, StateService} from '@uirouter/angularjs';
import naturalCompare from 'natural-compare-lite';
import {removeClusterItems, advancedSaveCache} from '../../../../store/actionCreators';
import ConfigureState from '../../../../services/ConfigureState';
import ConfigSelectors from '../../../../store/selectors';
import Caches from '../../../../services/Caches';
import Version from 'app/services/Version.service';
import {ShortCache} from '../../../../types';
import {IColumnDefOf} from 'ui-grid';

// Controller for Caches screen.
export default class PageConfigureCrudUICaches {
    static $inject = [
        'ConfigSelectors',
        'configSelectionManager',
        '$uiRouter',
        '$transitions',
        'ConfigureState',
        '$state',
        'IgniteVersion',
        'Caches'
    ];

    constructor(
        private ConfigSelectors,
        private configSelectionManager,
        private $uiRouter: UIRouter,
        private $transitions: TransitionService,
        private ConfigureState: ConfigureState,
        private $state: StateService,
        private Version: Version,
        private Caches: Caches
    ) {}

    openDrawer = false;
    visibleRows$ = new Subject();
    selectedRows$ = new Subject();

    cachesColumnDefs: Array<IColumnDefOf<ShortCache>> = [
        {
            name: 'name',
            displayName: 'Name',
            field: 'name',
            enableHiding: false,
            sort: {direction: 'asc', priority: 0},
            filter: {
                placeholder: 'Filter by name…'
            },
            sortingAlgorithm: naturalCompare,
            minWidth: 250,
            maxWidth: 350
        },
        {
            name: 'cacheMode',
            displayName: 'Mode',
            field: 'cacheMode',
            multiselectFilterOptions: this.Caches.cacheModes,
            width: 160
        },
        {
            name: 'atomicityMode',
            displayName: 'Atomicity',
            field: 'atomicityMode',
            multiselectFilterOptions: this.Caches.atomicityModes,
            width: 160
        },
        {
            name: 'backups',
            displayName: 'Backups',
            field: 'backups',
            width: 130,
            enableFiltering: false,
            cellTemplate: `
                <div class="ui-grid-cell-contents">{{ grid.appScope.$ctrl.Caches.getCacheBackupsCount(row.entity) }}</div>
            `
        },
        {
            name: 'actions',
            displayName: 'Actions',
            enableFiltering: false,
            field: 'name',
            cellTemplate: `
                <div class="ui-grid-cell-contents"><a class="link-success"  ui-sref="base.console.edit.advanced.models.model({modelID: row.entity.domains[0]})"
                    title='Click to visit cache domains' ></a>
                </div>
            `,         
            minWidth: 0,
        },
    ];

    $onInit() {
        const cacheID$ = this.$uiRouter.globals.params$.pipe(
            pluck('cacheID'),
            publishReplay(1),
            refCount()
        );

        this.shortCaches$ = this.ConfigureState.state$.pipe(this.ConfigSelectors.selectCurrentShortCaches);
        this.shortModels$ = this.ConfigureState.state$.pipe(this.ConfigSelectors.selectCurrentShortModels);
        this.originalCache$ = cacheID$.pipe(
            distinctUntilChanged(),
            switchMap((id) => {
                return this.ConfigureState.state$.pipe(this.ConfigSelectors.selectCacheToEdit(id));
            })
        );

        this.isNew$ = cacheID$.pipe(map((id) => id === 'new'));
        this.itemEditTitle$ = combineLatest(this.isNew$, this.originalCache$, (isNew, cache) => {
            return `${isNew ? 'Create' : 'Edit'} cache ${!isNew && !!cache && cache.name ? `‘${cache.name}’` : ''}`;
        });
        this.selectionManager = this.configSelectionManager({
            itemID$: cacheID$,
            selectedItemRows$: this.selectedRows$,
            visibleRows$: this.visibleRows$,
            loadedItems$: this.shortCaches$
        });

        this.subscription = merge(
            this.originalCache$,
            this.selectionManager.editGoes$.pipe(tap((id) => this.edit(id))),
            this.selectionManager.editLeaves$.pipe(tap((options) => this.$state.go('base.configuration.edit.crudui.caches', null, options)))
        ).subscribe();

        this.isBlocked$ = cacheID$;

        this.tableActions$ = this.selectionManager.selectedItemIDs$.pipe(map((selectedItems:Array<string>) => [
            {
                action: 'Clone',
                click: () => this.clone(selectedItems),
                available: selectedItems.length==1
            },
            {
                action: 'Delete',
                click: () => {
                    this.remove(selectedItems);
                },
                available: true
            }
        ]));
    }
    

    clone(itemIDs: Array<string>) {
        this.originalCache$.pipe(            
            switchMap((cache) => {
                let clonedCache = cloneDeep(cache);
                clonedCache.id = uuidv4();
                clonedCache.name = cache.name+'_cloned';
                this.ConfigureState.dispatchAction(
                    advancedSaveCache(clonedCache, false)
                );
                return clonedCache;
            })
        )
    }

    remove(itemIDs: Array<string>) {        
        this.ConfigureState.dispatchAction(
            removeClusterItems(this.$uiRouter.globals.params.clusterID, 'caches', itemIDs, true, true)
        );
    }

    $onDestroy() {
        this.subscription.unsubscribe();
        this.visibleRows$.complete();
        this.selectedRows$.complete();
    }

    edit(cacheID: string) {
        this.$state.go('base.configuration.edit.crudui.caches.cache', {cacheID});
    }

    save({cache, download}) {
        this.ConfigureState.dispatchAction(advancedSaveCache(cache, download));
    }
}
