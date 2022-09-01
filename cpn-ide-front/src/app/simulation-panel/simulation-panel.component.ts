import { ModelService } from "./../services/model.service";
import { Component, OnInit, OnDestroy } from "@angular/core";
import { AccessCpnService } from "../services/access-cpn.service";
import { SimulationService } from "../services/simulation.service";
import { ProjectService } from "../services/project.service";
import { ElectronService } from "ngx-electron";
import { MatDialog } from "@angular/material/dialog";
import { DialogComponent } from "../common/dialog/dialog.component";

@Component({
  selector: "app-simulation-panel",
  templateUrl: "./simulation-panel.component.html",
  styleUrls: ["./simulation-panel.component.scss"],
})
export class SimulationPanelComponent implements OnInit, OnDestroy {
  constructor(
    private projectService: ProjectService,
    private electronService: ElectronService,
    public accessCpnService: AccessCpnService,
    public modelService: ModelService,
    public simulationService: SimulationService,
    public dialog: MatDialog,
  ) {
    console.log(this.constructor.name, "constructor");
  }

  ngOnInit() {
    console.log(this.constructor.name, "ngOnInit()");

    this.simulationService.setMode(this.simulationService.mode);
  }

  ngOnDestroy() {}

  onRewind() {
    this.accessCpnService.initSim();
  }

  onAnimation() {
    this.simulationService.isAnimation = !this.simulationService.isAnimation;
  }

  onAutoswitchPages() {
    this.simulationService.isAutoswitchPage = !this.simulationService
      .isAutoswitchPage;
  }

  onRunMultiStep() {
    if (this.simulationService.multiStepCount > 0) {
      this.simulationService.multiStepCount = 0;
      return;
    }
    console.log(
      this.constructor.name,
      "runMultiStep(), simulationConfig = ",
      this.simulationService.simulationConfig
    );

    this.simulationService.multiStepCount = this.simulationService.simulationConfig.multi_step.steps;
    this.simulationService.runMultiStep();
  }

  onRunMultiStepFF() {
    console.log(
      this.constructor.name,
      "onRunMultiStepFF(), simulationConfig = ",
      this.simulationService.simulationConfig
    );
    this.simulationService.runMultiStepFF();
  }

  onRunReplication() {
    console.log(
      this.constructor.name,
      "onRunReplication(), simulationConfig = ",
      this.simulationService.simulationConfig
    );
    this.simulationService.runReplication();
  }

  onRunCreateLog(){
    console.log(
      this.constructor.name,
      "onRunCreateLog(), simulationConfig = ",
      this.simulationService.simulationConfig
    );
    this.onSaveLog(false);
  }

  onSaveLog(isSaveAs) {
    console.log(isSaveAs);
    const fs = this.accessCpnService.getFs();
    console.log(fs);
    console.log(this.modelService.projectName);
    console.log(this.modelService.logName);
    const dialogRef = this.dialog.open(DialogComponent, {
      width: "500px",
      data: {
        title: "Save the log to file",
        input: [
          { title: "Filename", value: this.modelService.projectName.replace(".cpn", "")},
        ],
      },
    });
    dialogRef.afterClosed().subscribe((data) => {
      if (data && data.result === DialogComponent.YES) {
        console.log(
          this.constructor.name,
          "onSaveProject(), YES clicked, data = ",
          data
        );

        // Save to file
        console.log(data.input[0].value);
        this.simulationService.runCreateLog(data.input[0].value);

        //this.simulationService.setOutputPath(data.input[0].value);
        //this.simulationService.getOutputPath();
        
        //this.simulationService.saveLogToFile(data.input[0].value);
        this.projectService.setModelName(data.input[0].value);

      }
    });
  }

  getMultistepProgress() {
    return (
      (100 *
        (this.simulationService.simulationConfig.multi_step.steps -
          this.simulationService.multiStepCount)) /
        this.simulationService.simulationConfig.multi_step.steps +
      "%"
    );
  }
}
