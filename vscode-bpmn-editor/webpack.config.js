//@ts-check
'use strict';

const path = require('path');

/** @type {import('webpack').Configuration} */
const extensionConfig = {
  name: 'extension',
  target: 'node',
  mode: 'none',

  entry: './src/extension/extension.ts',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'extension.js',
    libraryTarget: 'commonjs2'
  },
  externals: {
    vscode: 'commonjs vscode'
  },
  resolve: {
    extensions: ['.ts', '.js'],
    alias: {
      '@extension': path.resolve(__dirname, 'src/extension')
    }
  },
  module: {
    rules: [
      {
        test: /\.ts$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'ts-loader'
          }
        ]
      }
    ]
  },
  devtool: 'nosources-source-map',
  infrastructureLogging: {
    level: 'log'
  }
};

/** @type {import('webpack').Configuration} */
const webviewConfig = {
  name: 'webview',
  target: 'web',
  mode: 'none',

  entry: './src/webview/index.tsx',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'webview.js'
  },
  resolve: {
    extensions: ['.ts', '.tsx', '.js', '.jsx', '.css'],
    alias: {
      '@webview': path.resolve(__dirname, 'src/webview')
    },
    fallback: {
      path: false,
      fs: false
    }
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'ts-loader',
            options: {
              compilerOptions: {
                module: 'esnext'
              }
            }
          }
        ]
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader']
      },
      {
        test: /\.(png|jpg|gif|svg|eot|ttf|woff|woff2)$/,
        type: 'asset/resource'
      }
    ]
  },
  devtool: 'source-map'
};

module.exports = [extensionConfig, webviewConfig];
