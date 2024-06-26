

import _ from 'lodash';

export interface ListEditableNgModel<T> extends ng.INgModelController {
    $viewValue: T[],
    editListItem(item: T): void,
    editListIndex(index: number): void
}

export type ID = (string | number) & {tag: 'ItemID'}

export type ItemScope<T> = {$index: number, item: T, form: ng.IFormController} & ng.IScope

export default class ListEditable<T extends {id?: any}> {
    static $inject = ['$animate', '$element', '$transclude', '$timeout'];

    constructor(
        $animate: ng.animate.IAnimateService,
        public $element: JQLite,
        public $transclude: ng.ITranscludeFunction,
        private $timeout: ng.ITimeoutService
    ) {
        $animate.enabled($element, false);
        this.hasItemView = $transclude.isSlotFilled('itemView');

        this._cache = new Map();
    }

    ngModel: ListEditableNgModel<T>;
    hasItemView: boolean;
    private _cache: Map<ID, T>;

    id(item: T | undefined, index: number): ID {
        if (item && item.id)
            return item.id as ID;

        return index as ID;
    }

    $onDestroy() {
        this.$element = null;
    }

    $onInit() {
        this.ngModel.$isEmpty = (value) => {
            return !Array.isArray(value) || !value.length;
        };

        this.ngModel.editListItem = (item) => {
            this.$timeout(() => {
                this.startEditView(this.id(item, this.ngModel.$viewValue.indexOf(item)));
                // For some reason required validator does not re-run after adding an item,
                // the $validate call fixes the issue.
                this.ngModel.$validate();
            });
        };

        this.ngModel.editListIndex = (index) => {
            this.$timeout(() => {
                this.startEditView(this.id(this.ngModel.$viewValue[index], index));
                // For some reason required validator does not re-run after adding an item,
                // the $validate call fixes the issue.
                this.ngModel.$validate();
            });
        };
    }

    save(item: T, id: ID) {
        this.ngModel.$setViewValue(
            this.ngModel.$viewValue.map((v, i) => this.id(v, i) === id ? _.cloneDeep(item) : v)
        );
    }

    remove(id: ID): void {
        this.ngModel.$setViewValue(this.ngModel.$viewValue.filter((v, i) => this.id(v, i) !== id));
    }

    isEditView(id: ID): boolean {
        return this._cache.has(id);
    }

    getEditView(id: ID): T {
        return this._cache.get(id);
    }

    getItem(id: ID): T {
        return this.ngModel.$viewValue.find((v, i) => this.id(v, i) === id);
    }

    startEditView(id: ID) {
        this._cache.set(
            id,
            _.cloneDeep(this.getItem(id))
        );
    }

    stopEditView(data: T, id: ID, form: ng.IFormController) {
        // By default list-editable saves only valid values, but if you specify {allowInvalid: true}
        // ng-model-option, then it will always save. Be careful and pay extra attention to validation
        // when doing so, it's an easy way to miss invalid values this way.

        // Don't close if form is invalid and allowInvalid is turned off (which is default value)
        if (!form.$valid && !this.ngModel.$options.getOption('allowInvalid'))
            return;

        this._cache.delete(id);

        this.save(data, id);
    }
}
