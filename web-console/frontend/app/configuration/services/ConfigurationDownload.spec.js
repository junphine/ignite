

import Provider from './ConfigurationDownload';

import {suite, test} from 'mocha';
import {assert} from 'chai';
import {spy} from 'sinon';

const mocks = () => new Map([
    ['messages', {
        showError: spy()
    }],
    ['activitiesData', {
        post: spy()
    }],
    ['configuration', {
        populate: (value) => Promise.resolve(value),
        _clusters: [],
        read() {
            return Promise.resolve({clusters: this._clusters});
        }
    }],
    ['summaryZipper', spy((value) => Promise.resolve(value))],
    ['Version', {
        currentSbj: {
            getValue() {
                return '2.0';
            }
        }
    }],
    ['$q', Promise],
    ['Demo', {
        enabled: true
    }],
    ['PageConfigure', {
        getClusterConfiguration: () => Promise.resolve({clusters: [{id: 1, name: 'An Cluster'}]})
    }],
    ['IgniteConfigurationResource', {
        populate: () => Promise.resolve({clusters: []})
    }]
]);

const saverMock = () => ({
    saveAs: spy()
});

suite('page-configure, ConfigurationDownload service', () => {
    test('fails and shows error message when summary zipper fails', () => {
        const service = new Provider(...mocks().values());
        const cluster = {id: 1, name: 'An Cluster'};
        service.configuration._clusters = [cluster];
        service.summaryZipper = () => Promise.reject({message: 'Summary zipper failed.'});

        return service.downloadClusterConfiguration(cluster)
        .then(() => Promise.reject('Should not happen'))
        .catch(() => {
            assert.equal(
                service.messages.showError.getCall(0).args[0],
                'Failed to generate project files. Summary zipper failed.',
                'shows correct error message when summary zipper fails'
            );
        });
    });

    test('calls correct dependcies', () => {
        const service = new Provider(...mocks().values());
        service.saver = saverMock();
        const cluster = {id: 1, name: 'An Cluster'};
        service.configuration._clusters = [cluster];

        return service.downloadClusterConfiguration(cluster)
        .then(() => {
            assert.deepEqual(
                service.activitiesData.post.getCall(0).args[0],
                {action: '/configuration/download'},
                'submits activity data'
            );
            assert.deepEqual(service.summaryZipper.getCall(0).args, [{
                cluster,
                data: {},
                demoMode: true,
                targetVer: '2.0'
            }], 'summary zipper arguments are correct');
            assert.deepEqual(service.saver.saveAs.getCall(0).args, [
                {
                    cluster,
                    data: {},
                    demoMode: true,
                    targetVer: '2.0'
                },
                'An_Cluster-project.zip'
            ], 'saver arguments are correct');
        });
    });
});
