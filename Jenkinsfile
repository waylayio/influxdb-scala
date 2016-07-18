#!groovy

def util = new io.waylay.Util()

node {
  stage 'Build'

  checkout scm

  util.reportErrors {
    timeout(time: 15, unit: 'MINUTES') {
      try {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
            sh "sbt clean test"
        }
      }finally {
        step([$class: 'JUnitResultArchiver', testResults: '**/test-reports/*.xml'])
      }
    }
  }
}
