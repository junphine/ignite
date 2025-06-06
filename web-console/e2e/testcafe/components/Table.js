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

import {Selector, t} from 'testcafe';

const findCell = Selector((table, rowIndex, columnLabel) => {
    table = table();

    const columnIndex = [].constructor.from(
        table.querySelectorAll('.ui-grid-render-container:not(.left) .ui-grid-header-cell:not(.ui-grid-header-span)'),
        (e) => e.textContent
    ).findIndex((t) => t.includes(columnLabel));

    const row = table.querySelector(`.ui-grid-render-container:not(.left) .ui-grid-viewport .ui-grid-row:nth-of-type(${rowIndex + 1})`);
    const cell = row.querySelectorAll(`.ui-grid-cell`)[columnIndex];

    return cell;
});

export class Table {
    /** @param {ReturnType<Selector>} selector */
    constructor(selector) {
        this._selector = selector;
        this.title = this._selector.find('.panel-title');
        this.actionsButton = this._selector.find('.btn-ignite').withText('Actions');
        this.allItemsCheckbox = this._selector.find('[role="checkbox button"]');
    }

    /** @param {string} label */
    async performAction(label) {
        await t.hover(this.actionsButton).click(Selector('.dropdown-menu a').withText(label));
    }

    /**
     * Toggles grid row selection
     * @param {number} index Index of row, starting with 1
     */
    async toggleRowSelection(index) {
        await t.click(this._selector.find(`.ui-grid-pinned-container .ui-grid-row:nth-of-type(${index}) .ui-grid-selection-row-header-buttons`));
    }

    /**
     * @param {number} rowIndex
     * @param {string} columnLabel
     */
    findCell(rowIndex, columnLabel) {
        return Selector(findCell(this._selector, rowIndex, columnLabel));
    }
}
