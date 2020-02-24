# xcresults

A command line tool to extract test summaries & screenshots from Xcode 11 XCResult files.

## Installation

Download latest version from github releases: 

`wget https://github.com/eroshenkoam/xcresults/releases/latest/download/xcresults`

And make it executable: 

`chmod +x xcresults`

## Usage

`xcresults <command> <options>`

Below are a few examples of common commands. For further assistance, use the --help option on any command

### Export to Allure2 results

`xcresults export /path/to/Test.xcresult /path/to/outputDirectory`

After that you can generate Allure report by following command: 

`allure serve /path/to/outputDirectory`
