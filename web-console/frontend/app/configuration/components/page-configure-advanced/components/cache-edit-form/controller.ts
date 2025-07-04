
import _ from 'lodash';
import cloneDeep from 'lodash/cloneDeep';
import get from 'lodash/get';
import {tap} from 'rxjs/operators';
import {Menu} from 'app/types';
import {StateService} from '@uirouter/angularjs';
import LegacyConfirmFactory from 'app/services/Confirm.service';
import Version from 'app/services/Version.service';
import Caches from '../../../../services/Caches';
import FormUtilsFactory from 'app/services/FormUtils.service';

export default class CacheEditFormController {
    modelsMenu: Menu<string>;

    igfssMenu: Menu<string>;

    /** IGFS IDs to validate against. */
    igfsIDs: string[];

    onSave: ng.ICompiledExpression;

    cache: any;

    clonedCache: any;

    static $inject = ['$state','IgniteConfirm', 'IgniteVersion', '$scope', 'Caches', 'IgniteFormUtils'];

    constructor(
        private $state: StateService,
        private IgniteConfirm: ReturnType<typeof LegacyConfirmFactory>,
        private IgniteVersion: Version,
        private $scope: ng.IScope,
        private Caches: Caches,
        private IgniteFormUtils: ReturnType<typeof FormUtilsFactory>
    ) {}

    $onInit() {
        this.available = this.IgniteVersion.available.bind(this.IgniteVersion);

        const rebuildDropdowns = () => {
            this.$scope.affinityFunction = [
                {value: 'Rendezvous', label: 'Rendezvous'},
                {value: 'Custom', label: 'Custom'},
                {value: null, label: 'Default'}
            ];            
        };

        rebuildDropdowns();

        const filterModel = () => {
            if (this.clonedCache && get(this.clonedCache, 'affinity.kind') === 'Fair')
                this.clonedCache.affinity.kind = null;

        };

        this.subscription = this.IgniteVersion.currentSbj.pipe(
            tap(rebuildDropdowns),
            tap(filterModel)
        )
        .subscribe();

        // TODO: Do we really need this?
        this.$scope.ui = this.IgniteFormUtils.formUI();

        this.formActions = [
            {text: 'Save', icon: 'checkmark', click: () => this.save(false)},
            {text: 'Save and Download', icon: 'download', click: () => this.save(true)}
        ];
    }

    $onDestroy() {
        this.subscription.unsubscribe();
    }

    $onChanges(changes) {
        if (
            'cache' in changes && get(this.clonedCache, 'id') !== get(this.cache, 'id')
        ) {
            this.clonedCache = cloneDeep(changes.cache.currentValue);
            if (this.$scope.ui && this.$scope.ui.inputForm) {
                this.$scope.ui.inputForm.$setPristine();
                this.$scope.ui.inputForm.$setUntouched();
            }
        }
        if ('models' in changes)
            this.modelsMenu = (changes.models.currentValue || []).map((m) => ({value: m.id, label: m.valueType}));
        if ('igfss' in changes) {
            this.igfssMenu = (changes.igfss.currentValue || []).map((i) => ({value: i.id, label: i.name}));
            this.igfsIDs = (changes.igfss.currentValue || []).map((i) => i.id);
        }
    }

    getValuesToCompare() {
        return [this.cache, this.clonedCache].map(this.Caches.normalize);
    }

    goDomainEdit() {
        this.$state.go('base.configuration.edit.advanced.models.model', {modelID:this.cache.domains[0]});
    }

    save(download) {
        if (this.$scope.ui.inputForm.$invalid)
            return this.IgniteFormUtils.triggerValidation(this.$scope.ui.inputForm, this.$scope);
        this.onSave({$event: {cache: cloneDeep(this.clonedCache), download}});
    }

    reset = (forReal) => forReal ? this.clonedCache = cloneDeep(this.cache) : void 0;

    confirmAndReset() {
        return this.IgniteConfirm.confirm('Are you sure you want to undo all changes for current cache?')
        .then(this.reset);
    }

    clearImplementationVersion(storeFactory) {
        delete storeFactory.implementationVersion;
    }
}
