const path = require('path');
const fs = require('fs');
const HtmlWebpackPlugin = require('html-webpack-plugin');

// Custom plugin to generate resource listing file for Java to read
class ResourceListingPlugin {
    apply(compiler) {
        compiler.hooks.afterEmit.tapAsync('ResourceListingPlugin', (compilation, callback) => {
            const outputPath = compilation.outputOptions.path;
            const files = Object.keys(compilation.assets);

            // Add the listing file itself
            files.push('resource-listing.txt');

            // Write listing file
            const listingContent = files.join('\n');
            fs.writeFileSync(path.join(outputPath, 'resource-listing.txt'), listingContent);

            console.log('Generated resource-listing.txt with', files.length, 'files');
            callback();
        });
    }
}

module.exports = {
    entry: './src/index.tsx',
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'webview.js',
        clean: true,
        assetModuleFilename: '[name][ext]'  // Use original filenames for assets
    },
    resolve: {
        extensions: ['.tsx', '.ts', '.js', '.jsx']
    },
    module: {
        rules: [
            {
                test: /\.tsx?$/,
                use: 'ts-loader',
                exclude: /node_modules/
            },
            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader']
            },
            {
                test: /\.(woff|woff2|eot|ttf|otf|svg)$/i,
                type: 'asset/resource'
            }
        ]
    },
    plugins: [
        new HtmlWebpackPlugin({
            template: './src/index.html',
            filename: 'index.html',
            inject: 'body'
        }),
        new ResourceListingPlugin()
    ],
    devServer: {
        static: {
            directory: path.join(__dirname, 'dist')
        },
        port: 3000,
        hot: true,
        open: true
    },
    devtool: 'source-map'
};
