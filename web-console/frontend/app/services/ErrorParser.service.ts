

import isEmpty from 'lodash/isEmpty';
import {nonEmpty} from 'app/utils/lodashMixins';
import JavaTypes from 'app/services/JavaTypes.service';

const CAUSE_STR = 'Caused by: ';
const ERR_START_STR = ' err=';

export default class {
    static $inject = ['JavaTypes'];

    /**
     * @param {import('./JavaTypes.service').default} JavaTypes
     */
    constructor(JavaTypes) {
        this.JavaTypes = JavaTypes;
    }

    parse(err, prefix) {
        prefix = prefix || '';

        if (err) {
            if (err.hasOwnProperty('data'))
                err = err.data;

            if (err.hasOwnProperty('message')) {
                let msg = err.message;

                const traceIndex = msg.indexOf(', trace=');

                if (traceIndex > 0)
                    msg = msg.substring(0, traceIndex);

                const lastIdx = msg.lastIndexOf(ERR_START_STR);
                let msgEndIdx = msg.indexOf(']', lastIdx);

                if (lastIdx > 0 && msgEndIdx > 0) {
                    let startIdx = msg.indexOf('[', lastIdx);

                    while (startIdx > 0) {
                        const tmpIdx = msg.indexOf(']', msgEndIdx + 1);

                        if (tmpIdx > 0)
                            msgEndIdx = tmpIdx;

                        startIdx = msg.indexOf('[', startIdx + 1);
                    }
                }

                const causes = [];

                let causeIdx = err.message.indexOf(CAUSE_STR);

                while (causeIdx >= 0) {
                    // Find next ": " in cause message to skip exception class name.
                    const msgStart = err.message.indexOf(': ', causeIdx + CAUSE_STR.length) + 2;
                    const causeEndLine = err.message.indexOf('\n', msgStart);
                    const msgEnd = err.message.indexOf('[', msgStart);
                    const cause = err.message.substring(msgStart, msgEnd >= 0 && msgEnd < causeEndLine ? msgEnd : causeEndLine);

                    if (causes && causes[0] !== cause)
                        causes.unshift(cause);

                    causeIdx = err.message.indexOf(CAUSE_STR, causeEndLine);
                }

                return new ErrorParseResult(
                    prefix + (lastIdx >= 0
                        ? msg.substring(lastIdx + ERR_START_STR.length, msgEndIdx > 0 ? msgEndIdx : traceIndex)
                        : msg),
                    causes
                );
            }

            if (err.hasOwnProperty('error')) {
                err = err.error
            }

            if (nonEmpty(err.className)) {
                if (isEmpty(prefix))
                    prefix = 'Internal cluster error: ';

                return new ErrorParseResult(prefix + err.className);
            }

            return new ErrorParseResult(prefix + err);
        }

        return new ErrorParseResult(prefix + 'Internal error.');
    }

    extractFullMessage(err) {
        const clsName = _.isEmpty(err.className) ? '' : '[' + this.JavaTypes.shortClassName(err.className) + '] ';

        let msg = err.message || '';
        const traceIndex = msg.indexOf(', trace=');

        if (traceIndex > 0)
            msg = msg.substring(0, traceIndex);

        return clsName + (msg);
    }
}

/**
 * Information with error parsing result.
 */
export class ErrorParseResult {
    /** String with parsed error message. */
    message: String;

    /** List of error causes in reverse order. */
    causes: String[];

    /**
     * @param {String} message String with parsed error message.
     * @param {Array.<String>} causes List of error causes in reverse order.
     */
    constructor(message: String, causes = []) {
        this.message = message;
        this.causes = causes;
    }
}
