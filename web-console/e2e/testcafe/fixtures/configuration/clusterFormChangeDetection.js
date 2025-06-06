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
import {advancedNavButton} from '../../components/pageConfiguration';
import {pageAdvancedConfiguration} from '../../components/pageAdvancedConfiguration';
import {confirmation} from '../../components/confirmation';
import {scrollIntoView} from '../../helpers';

const regularUser = createRegularUser();

fixture('Cluster configuration form change detection')
    .before(async() => {
        await dropTestDB();
        await insertTestUser();
    })
    .beforeEach(async(t) => {
        await t.useRole(regularUser);
    })
    .after(dropTestDB);

test.skip('New cluster change detection', async(t) => {
    const overview = new PageConfigurationOverview();
    const advanced = new PageConfigurationAdvancedCluster();

    await t
        .navigateTo(resolveUrl(`/configuration/overview`))
        .click(overview.createClusterConfigButton)
        .click(advancedNavButton);

    await t.click(advanced.sections.connectorConfiguration.panel.heading);

    // IODO: Investigate why this code doesn't work in headless mode;
    await scrollIntoView.with({dependencies: {el: advanced.sections.connectorConfiguration.inputs.enable.control}})();

    await t
        .click(advanced.sections.connectorConfiguration.inputs.enable.control)
        .click(advanced.saveButton)
        .click(pageAdvancedConfiguration.cachesNavButton)
        .expect(confirmation.body.exists).notOk(`Doesn't show changes confirmation after saving new cluster`);
});
