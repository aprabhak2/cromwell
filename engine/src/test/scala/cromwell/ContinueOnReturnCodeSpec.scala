package cromwell

import akka.testkit.EventFilter
import cromwell.engine.WorkflowFailed
import cromwell.util.SampleWdl

class ContinueOnReturnCodeSpec extends CromwellTestkitSpec {
  "A workflow with tasks that produce non-zero return codes" should {
    "have correct contents in stdout/stderr files for a call that implicitly continues on return code" ignore {
      runWdlAndAssertWorkflowStdoutStderr(
        sampleWdl = SampleWdl.ContinueOnReturnCode,
        eventFilter = EventFilter.info(pattern = s"persisting status of A to Failed", occurrences = 1),
        stdout = Map("w.A" -> Seq("321\n")),
        stderr = Map("w.A" -> Seq("")),
        terminalState = WorkflowFailed
      )
    }

    "have correct contents in stdout/stderr files for a call that explicitly mentions continue on return code" ignore {
      runWdlAndAssertWorkflowStdoutStderr(
        sampleWdl = SampleWdl.ContinueOnReturnCode,
        runtime = "runtime {continueOnReturnCode: false}",
        eventFilter = EventFilter.info(pattern = s"persisting status of A to Failed", occurrences = 1),
        stdout = Map("w.A" -> Seq("321\n")),
        stderr = Map("w.A" -> Seq("")),
        terminalState = WorkflowFailed
      )
    }

    "have correct contents in stdout/stderr files for a call that does not continue on return code flag" ignore {
      runWdlAndAssertWorkflowStdoutStderr(
        sampleWdl = SampleWdl.ContinueOnReturnCode,
        runtime = "runtime {continueOnReturnCode: true}",
        eventFilter = EventFilter.info(pattern = s"persisting status of B to Done", occurrences = 1),
        stdout = Map("w.A" -> Seq("321\n"), "w.B" -> Seq("321\n")),
        stderr = Map("w.A" -> Seq(""), "w.B" -> Seq(""))
      )
    }

    "have correct contents in stdout/stderr files for a call that does not continue on return code value" ignore {
      runWdlAndAssertWorkflowStdoutStderr(
        sampleWdl = SampleWdl.ContinueOnReturnCode,
        runtime = "runtime {continueOnReturnCode: 123}",
        eventFilter = EventFilter.info(pattern = s"persisting status of B to Done", occurrences = 1),
        stdout = Map("w.A" -> Seq("321\n"), "w.B" -> Seq("321\n")),
        stderr = Map("w.A" -> Seq(""), "w.B" -> Seq(""))
      )
    }

    "have correct contents in stdout/stderr files for a call that does not continue on return code list" ignore {
      runWdlAndAssertWorkflowStdoutStderr(
        sampleWdl = SampleWdl.ContinueOnReturnCode,
        runtime = "runtime {continueOnReturnCode: [123]}",
        eventFilter = EventFilter.info(pattern = s"persisting status of B to Done", occurrences = 1),
        stdout = Map("w.A" -> Seq("321\n"), "w.B" -> Seq("321\n")),
        stderr = Map("w.A" -> Seq(""), "w.B" -> Seq(""))
      )
    }
  }
}