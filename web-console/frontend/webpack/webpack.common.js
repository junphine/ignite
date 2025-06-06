

const path = require('path');
const webpack = require('webpack');

const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ProgressBarPlugin = require('progress-bar-webpack-plugin');

const eslintFormatter = require('eslint-formatter-friendly');

const basedir = path.join(__dirname, '../');
const contentBase = path.join(basedir, 'public');
const app = path.join(basedir, 'app');

/** @type {webpack.Configuration} */
const config = {
    node: {
        fs: 'empty'
    },
    // Entry points.
    entry: {
        app: path.join(basedir, 'index.js'),
        browserUpdate: path.join(app, 'browserUpdate', 'index.js')
    },
    // Output system.
    output: {
        path: path.resolve('build'),
        filename: '[name].[chunkhash].js',
        publicPath: '/'
    },

    // Resolves modules.
    resolve: {
        // A list of module source folders.
        alias: {
            app,
            images: path.join(basedir, 'public/images'),
            libs: path.join(basedir, 'public/libs'),
            views: path.join(basedir, 'views')
        },
        extensions: ['.wasm', '.mjs', '.js', '.ts', '.json']
    },

    module: {
        rules: [
            // Exclude tpl.pug files to import in bundle.
            {
                test: /^(?:(?!tpl\.pug$).)*\.pug$/, // TODO: check this regexp for correct.
                use: {
                    loader: 'pug-html-loader',
                    options: {
                        basedir
                    }
                }
            },

            // Render .tpl.pug files to assets folder.
            {
                test: /\.tpl\.pug$/,
                use: [
                    'file-loader?exports=false&name=assets/templates/[name].[hash].html',
                    `pug-html-loader?exports=false&basedir=${basedir}`
                ]
            },
            { test: /\.worker\.js$/, use: { loader: 'worker-loader' } },
            {
                test: /\.(js|ts)$/,
                enforce: 'pre',
                exclude: [/node_modules/],
                use: [{
                    loader: 'eslint-loader',
                    options: {
                        formatter: eslintFormatter,
                        context: process.cwd()
                    }
                }]
            },
            {
                test: /\.(js|ts)$/,
                exclude: /node_modules/,
                use: 'babel-loader'
            },
            {
                test: /\.(ttf|eot|svg|woff(2)?)(\?v=[\d.]+)?(\?[a-z0-9#-]+)?$/,
                exclude: [contentBase, /\.icon\.svg$/],
                use: 'file-loader?name=assets/fonts/[name].[ext]'
            },
            {
                test: /\.icon\.svg$/,
                use: {
                    loader: 'svg-sprite-loader',
                    options: {
                        symbolRegExp: /\w+(?=\.icon\.\w+$)/,
                        symbolId: '[0]'
                    }
                }
            },
            {
                test: /.*\.url\.svg$/,
                include: [contentBase],
                use: 'file-loader?name=assets/fonts/[name].[ext]'
            },
            {
                test: /\.(jpe?g|png|gif)$/i,
                use: 'file-loader?name=assets/images/[name].[hash].[ext]'
            },
            {
                test: require.resolve('jquery'),
                use: [
                    'expose-loader?$',
                    'expose-loader?jQuery'
                ]
            },
            {
                test: require.resolve('nvd3'),
                use: 'expose-loader?nv'
            },
            {
                // Mark files inside `@angular/core` as using SystemJS style dynamic imports.
                // Removing this will cause deprecation warnings to appear.
                // https://github.com/angular/angular/issues/21560#issuecomment-433601967
                test: /[\/\\]@angular[\/\\]core[\/\\].+\.js$/,
                parser: { system: true } // enable SystemJS
            }
        ]
    },

    optimization: {
        splitChunks: {
            chunks: 'all'
        }
    },

    // Load plugins.
    plugins: [
        new webpack.DefinePlugin({
            WEB_CONSOLE_VERSION: JSON.stringify(require('../package.json').version)
        }),
        new webpack.ProvidePlugin({
            $: 'jquery',
            'window.jQuery': 'jquery',
            _: 'lodash',
            nv: 'nvd3'
        }),
        new webpack.optimize.AggressiveMergingPlugin({moveToParents: true}),
        new HtmlWebpackPlugin({
            template: path.join(basedir, './views/index.pug')
        }),
        new CopyWebpackPlugin([
            { context: 'public', from: '**/*.{png,jpg,svg,ico,js,html,css，eot,ttf,woff,woff2}'}
        ]),
        new ProgressBarPlugin()
    ]
};

module.exports = config;
