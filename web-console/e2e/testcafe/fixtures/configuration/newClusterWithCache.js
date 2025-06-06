/*
 * Copyright 2019 Ignite Systems, Inc. and Contributors.
 *
 * Licensed under the Ignite Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {dropTestDB, insertTestUser, resolveUrl} from '../../environment/envtools';
import {createRegularUser} from '../../roles';
import {PageConfigurationOverview} from '../../page-models/PageConfigurationOverview';
import {PageConfigurationAdvancedCluster} from '../../page-models/PageConfigurationAdvancedCluster';
import {configureNavButton} from '../../components/topNavigation';

const regularUser = createRegularUser();

fixture('New cluster with cache')
    .before(async() => {
        await dropTestDB();
        await insertTestUser();
    })
    .beforeEach(async(t) => {
        await t.useRole(regularUser);
    })
    .after(dropTestDB);

test(`New cluster name doesn't disappear`, async(t) => {
    const overview = new PageConfigurationOverview();
    const advanced = new PageConfigurationAdvancedCluster();

    await t
        .navigateTo(resolveUrl(`/configuration/new/advanced/caches/new`))
        .click(advanced.saveButton)
        .click(configureNavButton)
        .expect(overview.clustersTable.findCell(0, 'Name').textContent).contains('Cluster');
});
