

export default class PageConfigureAdvancedController {
    static menuItems = [
        { text: 'Cluster', sref: 'base.configuration.edit.advanced.cluster' },
        { text: 'SQL Scheme', sref: 'base.configuration.edit.advanced.models' },
        { text: 'Caches', sref: 'base.configuration.edit.advanced.caches' },
        { text: 'Services', sref: 'base.configuration.edit.advanced.services' },
        { text: 'IGFS', sref: 'base.configuration.edit.advanced.igfs' }
    ];

    menuItems: Array<{text: string, sref: string}>;

    $onInit() {
        this.menuItems = this.constructor.menuItems;
    }
}
