

import cloneDeep from 'lodash/cloneDeep';
import get from 'lodash/get';

import {Subject, Observable, combineLatest, from, of} from 'rxjs';
import {catchError,tap, map, refCount, pluck, take, filter, publishReplay, switchMap, distinctUntilChanged} from 'rxjs/operators';
import {Menu} from 'app/types';
import uuidv4 from 'uuid/v4';
import {default as ConfigureState} from 'app/configuration/services/ConfigureState';
import {default as ConfigSelectors} from 'app/configuration/store/selectors';
import LegacyConfirmFactory from 'app/services/Confirm.service';
import Version from 'app/services/Version.service';
import Caches from 'app/configuration/services/Caches';
import Clusters from 'app/configuration/services/Clusters';
import FormUtilsFactory from 'app/services/FormUtils.service';
import AgentManager from 'app/modules/agent/AgentManager.service';
import TaskFlows from 'app/console/services/TaskFlows';

export default class TaskFlowFormController {
    

    static $inject = ['IgniteConfirm', 'IgniteVersion', '$scope','ConfigureState','ConfigSelectors','Caches','Clusters', 'TaskFlows', 'IgniteFormUtils', 'AgentManager'];

    constructor(
        private IgniteConfirm: ReturnType<typeof LegacyConfirmFactory>,
        private IgniteVersion: Version,
        private $scope: ng.IScope,
        private ConfigureState: ConfigureState,
        private ConfigSelectors: ConfigSelectors,
        private Caches: Caches,
        private Clusters: Clusters,
        private TaskFlows: TaskFlows,
        private IgniteFormUtils: ReturnType<typeof FormUtilsFactory>,
        private AgentManager: AgentManager
    ) {
       this.taskFlow = this.TaskFlows.getBlankTaskFlow();
    }

    onSave: ng.ICompiledExpression;
    
    sourceCluster: object;

    models: Array<object>;
    
    selectCaches: Array<object>;    
    
    targetCluster: object;
    
    targetClusterId: string;
    
    $onInit() {        
        
        this.originalCluster$ = this.sourceCluster.pipe(
            filter((v) => v.length==1),
            distinctUntilChanged(),
            switchMap((items) => {
                this.sourceClusterId = items[0].id;    
                return from(this.Clusters.getConfiguration(this.sourceClusterId));               
            }),            
            publishReplay(1),
            refCount()
        );   
             
        this.subscrition = this.originalCluster$.subscribe((c) =>{
            if(c && c.data.cluster){
                let cluster = c.data.cluster;
                this.taskFlow.name = 'receive data from '+ cluster.name;
                this.taskFlow.group = this.targetCluster.name
                this.taskFlow.targetCluster = this.targetClusterId
                this.taskFlow.sourceCluster = cluster.id
                this.clonedTaskFlow = cloneDeep(this.taskFlow);
            }            
        });

        this.selectCaches = []

        this.targetCaches.subscribe((caches) => this.selectCaches = caches)
        
        this.$scope.ui = this.IgniteFormUtils.formUI();

        this.formActions = [
            {text: 'Save TaskFlow', icon: 'checkmark', click: () => this.confirmAndSave()},
            {text: 'Start TaskFlow', icon: 'copy', click: () => this.confirmAndStart()}                
        ];
    }   

    $onDestroy() {
        this.subscrition.unsubscribe();
    }
    
    buildTaskFlows(tplFlow){   
       this.$scope.message = ''   
       tplFlow.group = this.targetCluster.name;
       tplFlow.targetCluster = this.targetClusterId;
       let taskList = []
       if(this.selectCaches.length==0){
            this.$scope.message = 'Selected Caches length is 0, Please select at least two!';
            return []
       }
       for(let cache of this.selectCaches){
           this.taskFlow = Object.assign({},tplFlow);
           this.taskFlow.id = uuidv4();
           this.taskFlow.target = cache.name;
           this.taskFlow.source = cache.name;
           this.taskFlow.name = tplFlow.name +' to '+cache.name;
           taskList.push(this.taskFlow);
       }
       return taskList;
    }

    $onChanges(changes) {
        if (
            'taskFlow' in changes && get(this.clonedTaskFlow, 'id') !== get(this.taskFlow, 'id')
        ) {
            this.clonedTaskFlow = cloneDeep(changes.taskFlow.currentValue);
            if (this.$scope.ui && this.$scope.ui.inputForm) {
                this.$scope.ui.inputForm.$setPristine();
                this.$scope.ui.inputForm.$setUntouched();
            }
        }
        
    }

    getValuesToCompare() {
        return [this.taskFlow, this.clonedTaskFlow];
    }    
    
    saveTaskFlowForGrid(tplFlow) {
        let args = this.onSave({$event:{sourceCluster: tplFlow.sourceCluster,args: tplFlow}});
        
        let tasks = this.buildTaskFlows(tplFlow);
        let result = [];
        
        for(let task of tasks){           
            let stat = from(this.TaskFlows.saveBasic(task)).pipe(
                switchMap(({data}) => of(
                    {type: 'SAVE_TASK_FLOW'}
                )),
                catchError((error) => of({
                    type: 'SAVE_TASK_FLOW_ERR',
                    error: {
                        message: `Failed to save cluster task flow: ${error}.`
                    }
                }))
            );    
            result.push(stat);            
        }
        if(!result){
            this.$scope.message = 'no task save!';
        }        
    }
    
    startTaskFlowForGrid(tplFlow) {
        let tasks = this.buildTaskFlows(tplFlow);
        let result = [];
        let serviceName = 'computeTaskLoadService';
        let task = 'ContinuousMapperTask';
        for(let task of tasks){           
            let stat = from(this.TaskFlows.getTaskFlows(task.group,task.target,task.source)).pipe(
                switchMap(({data}) => of(                   
                    {type: 'LOAD_TASK_FLOW', taskFlow: data}                   
                )),
                catchError((error) => of({
                    type: 'LOAD_TASK_FLOW_ERR',
                    error: {
                        message: `Failed to save cluster task flow: ${error.data.message}.`
                    }
                }))
            );    
            result.push(stat);
        }
        if(result){
            this.AgentManager.callClusterService({id: tplFlow.targetCluster},serviceName,{tasks,task,models:this.models}).then((data) => {
                this.$scope.status = data.status; 
                if(data.message){
                    this.$scope.message = data.message;
                }
                if(data.result){
                    return data.result;
                }
                return {}
             })   
            .catch((e) => {
                 this.$scope.message = ('Failed to callClusterService : '+serviceName+' Caused : '+e);           
             });
        }
        
    }

    reset = (forReal) => forReal ? this.clonedTaskFlow = cloneDeep(this.taskFlow) : void 0;

    confirmAndSave() {
        if (this.$scope.ui.inputForm && this.$scope.ui.inputForm.$invalid)
            return this.IgniteFormUtils.triggerValidation(this.$scope.ui.inputForm, this.$scope);
        return this.IgniteConfirm.confirm('Are you sure you want to save task flow ' + this.taskFlow.name + ' for current grid?')
        .then(() => { this.saveTaskFlowForGrid(this.clonedTaskFlow); } );
    }
    
    confirmAndStart() {        
        return this.IgniteConfirm.confirm('Are you sure you want to start this task flow ' + this.taskFlow.name + ' for current grid?')
        .then(() => { this.startTaskFlowForGrid(this.clonedTaskFlow); } );
    }

    clearImplementationVersion(storeFactory) {
        delete storeFactory.implementationVersion;
    }
}
