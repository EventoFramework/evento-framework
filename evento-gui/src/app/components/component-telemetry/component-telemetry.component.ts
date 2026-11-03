import {Component, Input, OnDestroy, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {chartTheme, onThemeChange} from '../../services/utils';
import {PerformanceService} from "../../services/performance.service";
import {retry} from "rxjs";

@Component({
    selector: 'app-component-telemetry',
    templateUrl: './component-telemetry.component.html',
    styleUrls: ['./component-telemetry.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class ComponentTelemetryComponent implements OnInit, OnDestroy {
  @Input() component!: any;

  chartOptions: any = {
    series: [
      {
        name: "My-series",
        data: [10, 41, 35, 51, 49, 62, 69, 91, 148]
      }
    ],
    chart: {
      height: 350,
      type: "bar"
    },
    title: {
      text: "My First Angular Chart"
    },
    xaxis: {
      categories: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"]
    }
  };
  serviceTimeAggregation: 'AVG' | 'MAX' | 'MIN' = "AVG";
  datetimeFrom: string;
  datetimeTo: string;
  interval: number = 15*60;

  charts = []


  private stopThemeWatch = onThemeChange(() => this.fetchTs());

  constructor(private service: PerformanceService) {
  }

  ngOnDestroy() {
    this.stopThemeWatch();
  }


  formatDateToCustomString(date) {
    function padZero(value) {
      return value.toString().padStart(2, '0');
    }

    let year = date.getFullYear();
    let month = padZero(date.getMonth() + 1); // Months are zero-based
    let day = padZero(date.getDate());
    let hours = padZero(date.getHours());
    let minutes = padZero(date.getMinutes());

    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  ngOnInit() {
    let d = new Date(new Date().getTime() - (4 * 60 * 60 * 1000));
    this.datetimeFrom = this.formatDateToCustomString(d);
    d = new Date();
    this.datetimeTo = this.formatDateToCustomString(d);
    return this.fetchTs()
  }

  async fetchTs() {
    const data = await this.service.getComponentPerformanceTs(
      this.component.bundleId,
      this.component.componentName,
      new Date(this.datetimeFrom).toISOString(),
      new Date(this.datetimeTo).toISOString(),
      this.interval,
      this.serviceTimeAggregation
    )
    const theme = chartTheme();
    const charts = [];
    for (let handler of Object.keys(data)) {
      charts.push({
        id: handler,
        series: [
          {
          name: "Count",
          type: "column",
          color: theme.series.count,
          data: data[handler].map((e: any) => ({x:new Date(e.timestamp).getTime(), y:parseInt(e.count)}))
        },
          {
            name: "Service Time",
            type: "column",
            color: theme.series.service,
            data: data[handler].map((e: any) => ({x:new Date(e.timestamp).getTime(), y:e.serviceTime?.toFixed(2) || null}))
          }
        ],
        chart: {
          height: 350,
          foreColor: theme.text,
          fontFamily: theme.font,
          type: "line",
          stacked: false,
          id: handler,
          group: this.component.componentName
        },
        dataLabels: {
          enabled: false
        },
        tooltip: {
          theme: theme.tooltip
        },
        grid: {
          borderColor: theme.grid
        },
        stroke: {
          width: [1, 1, 4]
        },
        title: {
          text: handler,
          align: "left",
          offsetX: 0
        },
        xaxis: {
          type: 'datetime',
          labels: {
            datetimeUTC: false
          }
        },
        yaxis:[
          {
            seriesName: "Count",
            axisTicks: {
              show: true
            },
            axisBorder: {
              show: true,
              color: theme.grid
            },
            title: {
              text: "Handled Count"
            },
            tooltip: {
              enabled: true
            },
          },
          {
            seriesName: "Service Time",
            opposite: true,
            axisTicks: {
              show: true
            },
            axisBorder: {
              show: true,
              color: theme.grid
            },
            title: {
              text: "Service Time"
            }
          },
        ]
      })
    }
    this.charts = charts;
  }

  tsFormChanged() {
    this.fetchTs();
  }
}
