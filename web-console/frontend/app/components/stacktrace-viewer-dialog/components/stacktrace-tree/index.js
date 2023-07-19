

import './style.scss';
import template from './template.pug';
import controller from './controller';

export default {
    template,
    controller,
    bindings: {
        items: '<'
    }
};
