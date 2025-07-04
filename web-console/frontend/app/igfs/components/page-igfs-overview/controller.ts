import {Subject, Observable, from, of} from 'rxjs';
import {map,switchMap, distinctUntilChanged, catchError} from 'rxjs/operators';
import naturalCompare from 'natural-compare-lite';

import {UIRouter} from '@uirouter/angularjs';
import {ShortCluster} from 'app/configuration/types';
import ConfigureState from 'app/configuration/services/ConfigureState';
import Clusters from 'app/configuration/services/Clusters';

import AgentManager from 'app/modules/agent/AgentManager.service';
import {IColumnDefOf} from 'ui-grid';

const cellTemplate = (state) => `
    <div class="ui-grid-cell-contents">
        <a
            class="link-success"
            ui-sref="base.igfs.edit.basic({storageID: row.entity.id})"
            title='Click to Visit data'
        >{{ 'File Manager' }}</a> &nbsp;|&nbsp;
        <a
            class="link-success"
            ui-sref="base.igfs.edit.advanced({storageID: row.entity.id})"
            title='Click to Configure storage settings'
        >{{ 'Setting' }}</a>
    </div>
`;

export default class PageIgfsOverviewController {
    static $inject = [
        '$uiRouter',
        'ConfigureState',
        'AgentManager',
        'Clusters'
    ];
    

    constructor(
        private $uiRouter: UIRouter,
        private ConfigureState: ConfigureState,
        private AgentManager: AgentManager,
        private Clusters: Clusters,
    ) {}

    shortClusters$: Observable<Array<ShortCluster>>;    
    selectedRows$: Subject<Array<any>>;
    selectedRowsIDs$: Observable<Array<string>>;
    
    
    datasourceColumnDefs: Array<any> = [
        {
            name: 'Name',
            displayName: 'Storage Id',
            field: 'name',
            enableHiding: false,
            enableFiltering: true,
            sort: {direction: 'asc'},
            sortingAlgorithm: naturalCompare,            
            width: 150
        },
        {
            name: 'Comment',
            displayName: 'Display Name',
            field: 'comment',
            enableHiding: false,
            enableFiltering: true,
            sort: {direction: 'asc'},
            sortingAlgorithm: naturalCompare,            
            width: 200
        },
        {
            name: 'url',
            displayName: 'Storage URL',
            field: 'url',
            filter: {
                placeholder: 'Filter by keyword…'
            },
            sort: {direction: 'asc'},
            sortingAlgorithm: naturalCompare,
            minWidth: 250
        },       
        {
            name: 'accessMode',
            displayName: 'Access Mode',
            field: 'accessMode',                   
            enableFiltering: false,           
            width: 150
        },        
        {
            name: 'status',
            displayName: 'Status',
            field: 'status',
            cellClass: 'ui-grid-number-cell',                
            cellTemplate: `
                <div class="ui-grid-cell-contents status-{{ row.entity.status }} ">{{ row.entity.status }}</div>
            `,
            enableFiltering: false,
            type: 'string',
            width: 100
        },
        {
            name: 'id',
            displayName: 'Action',
            field: 'id',
            cellClass: 'ui-grid-number-cell',                
            cellTemplate: cellTemplate,
            enableFiltering: false,
            type: 'string',
            width: 200
        }
    ];
    
    // 创建一个函数，用于从localStorage中读取JSON数组并返回Observable流
    getLocalStorageJsonList(key) {
        return new Observable((observer) => {
            try {
                // 尝试从localStorage中读取数据
                const jsonString = localStorage.getItem(key);
                if (jsonString === null) {
                    observer.next([]); // 如果没有找到对应的key，发送空数组
                    observer.complete();
                } else {                    
                    const obj = JSON.parse(jsonString);
                    const list = Object.keys(obj).map((key) => obj[key]);
                    if (!Array.isArray(list)) {
                        observer.error(new Error('存储的值不是一个JSON数组'));
                    } else {                       
                        observer.next(list);
                    }
                }
            } catch (error) {
                observer.error(error); // 如果发生错误，发送错误通知
            }
        });
    }


    editDatasource(cluster) {
        return this.$uiRouter.stateService.go('^.edit', {storageID: cluster.id});
    }
    
    pingDatasource(clusters: Array<any>) {
        for (let cluster of clusters) {
            this.AgentManager.callClusterService(cluster, 'storageTest', cluster).then((msg) => {
                if (msg.status) {
                    cluster.status = msg.status;
                }

            });
        }
    }

    $onInit() {
        this.shortClusters$ = from(this.Clusters.getClustersOverview().then(({data}) => data));

        // 尝试从localStorage中读取数据
        const jsonString = localStorage.igfsStorages;
        let storageSetting = {};
        if (jsonString) {
            storageSetting = JSON.parse(jsonString);
        }

        this.shortClusters$.subscribe((data)=> {
            const dataSourceList = data.filter((item) => item.igfssCount > 0 || storageSetting[item.name]);

            this.dataSourceList = dataSourceList.map((cluster) => {
                cluster.id = cluster.name;
                const storage = storageSetting[cluster.name];
                if(storage)
                    return { ...cluster,  ...storage };
                return cluster;
            });
        }); 
        
        this.selectedRows$ = new Subject();
        
        this.selectedRowsIDs$ = this.selectedRows$.pipe(map((selectedDatasources) => selectedDatasources.map((cluster) => cluster.id)));
        
        this.actions$ = this.selectedRows$.pipe(map((selectedDatasource) => [
            {
                action: 'Ping',
                click: () => this.pingDatasource(selectedDatasource),
                available: selectedDatasource.length >= 1
            }  
        ]));
    }
    
    $onDestroy() {
        this.selectedRows$.complete();
    }
}
