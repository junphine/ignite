/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import _ from 'lodash';

import { BehaviorSubject } from 'rxjs';
import {tap, filter, combineLatest} from 'rxjs/operators';
import {CancellationError} from 'app/errors/CancellationError';

export default class {
    static $inject = ['AgentManager', 'IgniteConfirm', 'IgniteVersion', 'IgniteMessages'];

    /**
     * @param agentMgr Agent manager.
     * @param Confirm Confirmation service.
     * @param Version Version check service.
     * @param Messages Messages service.
     */
    constructor(agentMgr, Confirm, Version, Messages) {
        this.agentMgr = agentMgr;
        this.Confirm = Confirm;
        this.Version = Version;
        this.Messages = Messages;

        this.clusters = [];
        this.db_clusters = [];
        this.isDemo = agentMgr.isDemoMode();
        this._inProgressSubject = new BehaviorSubject(false);
    }

    $onInit() {
        if (this.isDemo)
            return;

        this.inProgress$ = this._inProgressSubject.asObservable();

        this.clusters$ = this.agentMgr.connectionSbj.pipe(
            combineLatest(this.inProgress$),
            tap(([sbj, inProgress]) => this.inProgress = inProgress),
            filter(([sbj, inProgress]) => !inProgress),
            tap(([{cluster, clusters}]) => {
                this.cluster = cluster ? {...cluster} : null;
                this.clusters = _.orderBy(clusters, ['name'], ['asc']);
                this.db_clusters = angular.copy(this.clusters);
                let db_clusters2 = angular.copy(this.clusters);
                let len = this.db_clusters.length;
                for(var i=0; i<len; i++) {
                	this.db_clusters[i].active = false;
                    this.db_clusters[i].name += '->RDS';
                    this.db_clusters[i].router_uri = 'rds';
                }
                for(var i=0; i<len; i++) {
                	db_clusters2[i].active = false;
                    db_clusters2[i].name += '->Presto';
                    db_clusters2[i].router_uri = 'presto';
                }
                this.db_clusters = this.db_clusters.concat(db_clusters2);
            })
        )
        .subscribe(() => {});
    }

    $onDestroy() {
        if (!this.isDemo)
            this.clusters$.unsubscribe();
    }

    change(item) {
        this.agentMgr.switchCluster(item)
            .then(() => this.cluster = item)
            .catch((err) => {
                if (!(err instanceof CancellationError))
                    this.Messages.showError('Failed to switch cluster: ', err);
            });
    }
    // add@byron 发送到rdbm或者flink sql
    changeTo(item,uri) {
    	this.agentMgr.switchCluster(item)
          .then(() => this.cluster = item)
          .catch((err) => {
              if (!(err instanceof CancellationError))
                  this.Messages.showError('Failed to switch cluster: ', err);
           });        
    }
    // end@
    isChangeStateAvailable() {
        return !this.isDemo && this.cluster && this.Version.since(this.cluster.clusterVersion, '2.0.0');
    }

    toggle($event) {
        $event.preventDefault();

        const toggleClusterState = () => {
            this._inProgressSubject.next(true);

            return this.agentMgr.toggleClusterState()
                .then(() => this._inProgressSubject.next(false))
                .catch((err) => {
                    this._inProgressSubject.next(false);

                    this.Messages.showError('Failed to toggle cluster state: ', err);
                });
        };

        if (this.cluster.active) {
            return this.Confirm.confirm('Are you sure you want to deactivate cluster?')
                .then(() => toggleClusterState());
        }

        return toggleClusterState();
    }
}
