import _ from 'lodash';

const DATE_OPTS = {
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
};

export default class StringBuilder {
    generatedBy() {
        return `This file was generated by Ignite Web Console (${new Date().toLocaleString('en-US', DATE_OPTS)})`;
    }

    /**
     * @param deep
     * @param indent
     */
    constructor(deep = 0, indent = 4) {
        this.indent = indent;
        this.deep = deep;
        this.lines = [];
    }

    emptyLine() {
        this.lines.push('');

        return this;
    }

    append(lines) {
        if (_.isArray(lines))
            _.forEach(lines, (line) => this.lines.push(_.repeat(' ', this.indent * this.deep) + line));
        else
            this.lines.push(_.repeat(' ', this.indent * this.deep) + lines);

        return this;
    }

    startBlock(lines) {
        this.append(lines);

        this.deep++;

        return this;
    }

    endBlock(line) {
        this.deep--;

        this.append(line);

        return this;
    }

    asString() {
        return this.lines.join('\n');
    }
}
