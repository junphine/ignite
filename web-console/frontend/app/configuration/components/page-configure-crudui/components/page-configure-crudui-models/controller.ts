import cloneDeep from 'lodash/cloneDeep';
import uuidv4 from 'uuid/v4';
import {Subject, Observable, combineLatest, merge} from 'rxjs';
import {pluck, tap, publishReplay, refCount, distinctUntilChanged, switchMap, map} from 'rxjs/operators';

import get from 'lodash/get';

import hasIndexTemplate from 'app/configuration/components/page-configure-advanced/components/page-configure-advanced-models/hasIndex.template.pug';
import keyCellTemplate from 'app/configuration/components/page-configure-advanced/components/page-configure-advanced-models/keyCell.template.pug';
import valueCellTemplate from 'app/configuration/components/page-configure-advanced/components/page-configure-advanced-models/valueCell.template.pug';

import {removeClusterItems, advancedSaveModel} from '../../../../store/actionCreators';

import {default as ConfigSelectors} from '../../../../store/selectors';
import {default as ConfigureState} from '../../../../services/ConfigureState';
import {default as Models} from '../../../../services/Models';

import {UIRouter, StateService} from '@uirouter/angularjs';
import {ShortDomainModel, DomainModel, ShortCache} from '../../../../types';
import {IColumnDefOf} from 'ui-grid';
import ConfigSelectionManager from '../../../../services/ConfigSelectionManager';


export default class PageConfigureCrudUIModels {
    static $inject = ['ConfigSelectors', 'ConfigureState', '$uiRouter', 'Models', '$state', 'configSelectionManager'];

    constructor(
        private ConfigSelectors: ConfigSelectors,
        private ConfigureState: ConfigureState,
        private $uiRouter: UIRouter,
        private Models: Models,
        private $state: StateService,
        private configSelectionManager: ReturnType<typeof ConfigSelectionManager>
    ) {}

    openDrawer = false;
    visibleRows$: Subject<Array<ShortDomainModel>>;
    selectedRows$: Subject<Array<ShortDomainModel>>;
    columnDefs: Array<IColumnDefOf<ShortDomainModel>>;
    itemID$: Observable<string>;
    shortItems$: Observable<Array<ShortDomainModel>>;
    originalItem$: Observable<DomainModel>;

    $onDestroy() {
        this.subscription.unsubscribe();
        this.visibleRows$.complete();
        this.selectedRows$.complete();
    }
    $onInit() {
        this.visibleRows$ = new Subject();

        this.selectedRows$ = new Subject();

        this.columnDefs = [
            {
                name: 'hasIndex',
                displayName: 'Indexed',
                field: 'hasIndex',
                type: 'boolean',
                enableFiltering: true,
                visible: true,
                multiselectFilterOptions: [{value: true, label: 'Yes'}, {value: false, label: 'No'}],
                width: 100,
                cellTemplate: hasIndexTemplate
            },
            {
                name: 'keyType',
                displayName: 'Key type',
                field: 'keyType',
                enableHiding: false,
                filter: {
                    placeholder: 'Filter by key type…'
                },
                cellTemplate: keyCellTemplate,
                width: 165
            },
            {
                name: 'valueType',
                displayName: 'Value type',
                field: 'valueType',
                enableHiding: false,
                filter: {
                    placeholder: 'Filter by value type…'
                },
                sort: {direction: 'asc', priority: 0},
                cellTemplate: valueCellTemplate,
                minWidth: 200,
                maxWidth: 350
            },
            {
                name: 'valueLabel',
                displayName: 'Value label',
                field: 'tableComment',
                enableHiding: false,
                filter: {
                    placeholder: 'Filter by value label'
                },
                sort: {direction: 'asc', priority: 0},
                cellTemplate: valueCellTemplate,
                minWidth: 200
            }           
        ];

        this.itemID$ = this.$uiRouter.globals.params$.pipe(pluck('modelID'));

        this.shortItems$ = this.ConfigureState.state$.pipe(
            this.ConfigSelectors.selectCurrentShortModels,
            tap((shortModels = []) => {
                const value = shortModels.every((m) => m.hasIndex);
                this.columnDefs[0].visible = !value;
            }),
            publishReplay(1),
            refCount()
        );

        this.shortCaches$ = this.ConfigureState.state$.pipe(this.ConfigSelectors.selectCurrentShortCaches);

        this.originalItem$ = this.itemID$.pipe(
            distinctUntilChanged(),
            switchMap((id) => {
                return this.ConfigureState.state$.pipe(this.ConfigSelectors.selectModelToEdit(id));
            }),
            distinctUntilChanged(),
            publishReplay(1),
            refCount()
        );

        this.isNew$ = this.itemID$.pipe(map((id) => id === 'new'));

        this.itemEditTitle$ = combineLatest(this.isNew$, this.originalItem$, (isNew, item) => {
            return `${isNew ? 'Create' : 'Edit'} model ${!isNew && get(item, 'valueType') ? `‘${get(item, 'valueType')}’` : ''}`;
        });

        this.selectionManager = this.configSelectionManager({
            itemID$: this.itemID$,
            selectedItemRows$: this.selectedRows$,
            visibleRows$: this.visibleRows$,
            loadedItems$: this.shortItems$
        });

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

        this.subscription = merge(
            this.originalItem$,
            this.selectionManager.editGoes$.pipe(tap((id) => this.edit(id))),
            this.selectionManager.editLeaves$.pipe(tap((options) => this.$state.go('base.configuration.edit.crudui.models', null, options)))
        ).subscribe();
    }

    edit(modelID) {
        this.$state.go('base.configuration.edit.crudui.models.model', {modelID});
    }

    save({model, download}) {        
        this.ConfigureState.dispatchAction(advancedSaveModel(model, download));
    }

    clone(itemIDs: Array<string>) {
        this.originalItem$.pipe(            
            switchMap((cache) => {
                let clonedCache = cloneDeep(cache);
                clonedCache.id = uuidv4();
                clonedCache.valueType = cache.valueType+'_cloned';
                this.ConfigureState.dispatchAction(
                    advancedSaveModel(clonedCache, false)
                );
                return clonedCache;
            })
        )
    }

    remove(itemIDs: Array<string>) {
        this.ConfigureState.dispatchAction(
            removeClusterItems(this.$uiRouter.globals.params.clusterID, 'models', itemIDs, true, true)
        );
    }
}
