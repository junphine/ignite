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

import {Selector} from 'testcafe';

export class PanelCollapsible {
    constructor(title) {
        this._selector = Selector('.panel-collapsible__title').withText(title).parent('panel-collapsible');
        this.heading = this._selector.find('.panel-collapsible__heading');
        this.body = this._selector.find('.panel-collapsible__content').addCustomDOMProperties({
            isOpened: (el) => !el.classList.contains('ng-hide')
        });
    }
}

export class AngularPanelCollapsible {
    constructor(title) {
        this._selector = Selector('panel-collapsible-angular>.heading>.title').withText(title).parent('panel-collapsible-angular');
        this.heading = this._selector.find('.heading');
        this.body = this._selector.addCustomDOMProperties({
            isOpened: (el) => el.hasAttribute('open')
        });
    }
}
