

import get from 'lodash/get';
import {Observable, combineLatest, from, of} from 'rxjs';
import {map, tap, pluck, take, filter, catchError, distinctUntilChanged, switchMap, publishReplay, refCount} from 'rxjs/operators';
import ConfigureState from 'app/configuration/services/ConfigureState';
import {UIRouter} from '@uirouter/angularjs';

export default class PageIgfsController {
    static $inject = ['$uiRouter', 'ConfigureState'];

    constructor(
        private $uiRouter: UIRouter,
        private ConfigureState: ConfigureState
    ) {}

    clusterID$: Observable<string>;
    clusterName$: Observable<string>;
    tooltipsVisible = true;

    $onInit() {
        this.clusterID$ = this.$uiRouter.globals.params$.pipe(pluck('storageID'));
        
        let cluster$ = this.clusterID$.pipe(
            distinctUntilChanged(),
            switchMap((id) => {
                return from(of(this._loadMongoExpress(id)));
            }),
            distinctUntilChanged(),
            publishReplay(1),
            refCount()
        );  
               

        const isNew$ = this.clusterID$.pipe(map((v) => v === 'new'));

        this.clusterName$ = combineLatest(cluster$, isNew$, (cluster, isNew) => {
            return `${isNew ? 'Create' : 'Edit'} Object Storage configuration 
            ${isNew ? '' : `‘${get(cluster, 'clusterName','')}’`}`;
        });
    }

    _loadMongoExpress(id: string) {
        try {            
            const mongoExpress = JSON.parse(localStorage.igfsStorages);
            if (mongoExpress && mongoExpress[id]) {            
                return mongoExpress[id];
            }
        }
        catch (ignored) {
            
        }
        return {id: id, clusterName: "Ignite FileSystem", url: ""}
    }

    $onDestroy() {}
}
