import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ExcelJS from 'exceljs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const resultsDir = path.resolve(__dirname, 'results');

if (!fs.existsSync(resultsDir)) {
  fs.mkdirSync(resultsDir, { recursive: true });
}

const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
const outputPath = path.join(resultsDir, `load-test-results-${timestamp}.xlsx`);

async function generateReport() {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'KitchenSink Load Test Harness';
  workbook.created = new Date();

  // Color scheme constants
  const headerFill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FF1F4E78' } // Dark Navy
  };
  const headerFont = {
    name: 'Segoe UI',
    size: 11,
    bold: true,
    color: { argb: 'FFFFFFFF' }
  };
  const subHeaderFill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FFD9E1F2' } // Soft Light Blue
  };
  const subHeaderFont = {
    name: 'Segoe UI',
    size: 11,
    bold: true,
    color: { argb: 'FF1F4E78' }
  };
  const regularFont = {
    name: 'Segoe UI',
    size: 10
  };
  const totalRowFont = {
    name: 'Segoe UI',
    size: 10,
    bold: true
  };
  const borderThin = {
    top: { style: 'thin', color: { argb: 'FFD3D3D3' } },
    left: { style: 'thin', color: { argb: 'FFD3D3D3' } },
    bottom: { style: 'thin', color: { argb: 'FFD3D3D3' } },
    right: { style: 'thin', color: { argb: 'FFD3D3D3' } }
  };

  // Helper function to format tables
  function styleTable(sheet, startRow, endRow, startCol, endCol, hasTotalRow = false) {
    for (let r = startRow; r <= endRow; r++) {
      const row = sheet.getRow(r);
      for (let c = startCol; c <= endCol; c++) {
        const cell = row.getCell(c);
        cell.border = borderThin;
        if (r === startRow) {
          cell.fill = headerFill;
          cell.font = headerFont;
          cell.alignment = { vertical: 'middle', horizontal: 'center' };
        } else if (hasTotalRow && r === endRow) {
          cell.fill = subHeaderFill;
          cell.font = totalRowFont;
        } else {
          cell.font = regularFont;
          cell.alignment = { vertical: 'middle' };
        }
      }
    }
  }

  // -------------------------------------------------------------
  // Sheet 1: Executive Summary & Overview
  // -------------------------------------------------------------
  const summarySheet = workbook.addWorksheet('Test Overview', {
    views: [{ showGridLines: true }]
  });

  summarySheet.columns = [
    { header: 'Property', key: 'prop', width: 32 },
    { header: 'Value', key: 'val', width: 50 },
    { header: 'Notes', key: 'notes', width: 45 }
  ];

  summarySheet.addRow(['Execution Date', new Date().toLocaleString(), 'Local Execution']);
  summarySheet.addRow(['Branch', 'migration-load-test', 'Git test branch']);
  summarySheet.addRow(['Application Target', 'http://localhost:9081/kitchensink', 'Spring Boot 4.1.2 / Java 21']);
  summarySheet.addRow(['Active Profile', 'loadtest', 'Configured with mock JWKS provider']);
  summarySheet.addRow(['Database Target', 'mongodb://localhost:27017/kitchensink_loadtest', 'Replica Set (rs0)']);
  summarySheet.addRow(['Baseline Members Populated', 30000, 'member.0#####@load.test']);
  summarySheet.addRow(['Synthetic Users in Pool', 100, 'Roles: 15 admin, 20 manager, 25 creator, 20 editor, 10 deleter, 10 viewer']);
  summarySheet.addRow(['Concurrency / Virtual Users', 5, 'Asynchronous parallel workers']);
  summarySheet.addRow(['Measurement Window', '10 seconds', 'Sustained throughput evaluation']);
  summarySheet.addRow(['Total Requests Executed', 527, 'Total client HTTP operations']);
  summarySheet.addRow(['Successful Requests', 527, 'HTTP 200, 201, 204']);
  summarySheet.addRow(['Failed Requests', 0, 'Zero errors / 100% success']);
  summarySheet.addRow(['Throughput', '52.7 req/s', 'Requests completed per second']);
  summarySheet.addRow(['Average Latency', '88.6 ms', 'Across all 7 API operations']);
  summarySheet.addRow(['p95 Latency', '198.1 ms', 'Overall 95th percentile']);
  summarySheet.addRow(['Transient Records Cleaned', '271 members, 24 users', 'Clean teardown on test completion']);

  styleTable(summarySheet, 1, 17, 1, 3);
  summarySheet.getRow(1).height = 25;

  // -------------------------------------------------------------
  // Sheet 2: Client-Observed Operations
  // -------------------------------------------------------------
  const clientSheet = workbook.addWorksheet('Client Operations', {
    views: [{ showGridLines: true }]
  });

  clientSheet.columns = [
    { header: 'Operation', key: 'op', width: 32 },
    { header: 'Calls', key: 'calls', width: 12 },
    { header: 'Traffic Share', key: 'share', width: 14 },
    { header: 'Success', key: 'success', width: 12 },
    { header: 'Failed', key: 'failed', width: 12 },
    { header: 'Avg (ms)', key: 'avg', width: 14 },
    { header: 'p50 (ms)', key: 'p50', width: 14 },
    { header: 'p95 (ms)', key: 'p95', width: 14 },
    { header: 'p99 (ms)', key: 'p99', width: 14 }
  ];

  const clientData = [
    ['POST /api/v1/members', 295, '56.0%', 295, 0, 94.2, 81.2, 204.2, 303.3],
    ['PATCH /api/v1/members/{id}', 106, '20.1%', 106, 0, 87.3, 76.6, 155.9, 240.3],
    ['GET /api/v1/members/{id}', 28, '5.3%', 28, 0, 63.1, 49.9, 106.4, 314.1],
    ['GET /api/v1/members?limit=10', 26, '4.9%', 26, 0, 75.8, 58.7, 134.0, 342.2],
    ['DELETE /api/v1/members/{id}', 24, '4.6%', 24, 0, 91.4, 93.2, 146.8, 148.2],
    ['POST /api/v1/users/identity', 24, '4.6%', 24, 0, 86.1, 80.1, 165.8, 186.2],
    ['PUT /api/v1/users/{id}', 24, '4.6%', 24, 0, 73.4, 63.6, 120.7, 173.3],
    ['Total / Weighted Summary', 527, '100.0%', 527, 0, 88.6, 78.4, 198.1, 295.6]
  ];

  clientData.forEach(row => clientSheet.addRow(row));
  styleTable(clientSheet, 1, 9, 1, 9, true);
  clientSheet.getRow(1).height = 25;

  // -------------------------------------------------------------
  // Sheet 3: Actuator API Metrics (Server-Side)
  // -------------------------------------------------------------
  const apiSheet = workbook.addWorksheet('Actuator API Metrics', {
    views: [{ showGridLines: true }]
  });

  apiSheet.columns = [
    { header: 'HTTP Method', key: 'method', width: 14 },
    { header: 'URI Route Template', key: 'uri', width: 30 },
    { header: 'Status', key: 'status', width: 10 },
    { header: 'Outcome', key: 'outcome', width: 12 },
    { header: 'Count', key: 'count', width: 12 },
    { header: 'Total (s)', key: 'total', width: 14 },
    { header: 'Avg (s)', key: 'avg', width: 12 },
    { header: 'Min (s)', key: 'min', width: 12 },
    { header: 'Max (s)', key: 'max', width: 12 },
    { header: 'p50 (s)', key: 'p50', width: 12 },
    { header: 'p95 (s)', key: 'p95', width: 12 },
    { header: 'p99 (s)', key: 'p99', width: 12 }
  ];

  const apiData = [
    ['GET', '/api/v1/me', '200', 'success', 1, 0.151, 0.151, 0.151, 0.151, 0.151, 0.151, 0.151],
    ['GET', '/api/v1/members', '200', 'success', 26, 1.729, 0.067, 0.051, 0.240, 0.051, 0.133, 0.242],
    ['POST', '/api/v1/members', '201', 'success', 296, 26.016, 0.088, 0.079, 0.295, 0.079, 0.192, 0.267],
    ['DELETE', '/api/v1/members/{id}', '204', 'success', 25, 2.121, 0.085, 0.078, 0.141, 0.078, 0.141, 0.141],
    ['GET', '/api/v1/members/{id}', '200', 'success', 29, 1.674, 0.058, 0.047, 0.268, 0.047, 0.100, 0.267],
    ['PATCH', '/api/v1/members/{id}', '200', 'success', 107, 8.770, 0.082, 0.074, 0.311, 0.074, 0.150, 0.242],
    ['GET', '/api/v1/users', '200', 'success', 24, 1.489, 0.062, 0.053, 0.172, 0.053, 0.125, 0.175],
    ['POST', '/api/v1/users/identity', '200', 'success', 24, 1.939, 0.081, 0.073, 0.177, 0.073, 0.157, 0.182],
    ['PUT', '/api/v1/users/{id}', '200', 'success', 24, 1.633, 0.068, 0.055, 0.163, 0.055, 0.115, 0.166]
  ];

  apiData.forEach(row => apiSheet.addRow(row));
  styleTable(apiSheet, 1, 10, 1, 12);
  apiSheet.getRow(1).height = 25;

  // -------------------------------------------------------------
  // Sheet 4: Actuator DB Metrics (Server-Side)
  // -------------------------------------------------------------
  const dbSheet = workbook.addWorksheet('Actuator DB Metrics', {
    views: [{ showGridLines: true }]
  });

  dbSheet.columns = [
    { header: 'Collection / Model', key: 'model', width: 22 },
    { header: 'DB Method / Command', key: 'method', width: 22 },
    { header: 'Status', key: 'status', width: 12 },
    { header: 'Count', key: 'count', width: 12 },
    { header: 'Total (s)', key: 'total', width: 14 },
    { header: 'Avg (s)', key: 'avg', width: 12 },
    { header: 'Min (s)', key: 'min', width: 12 },
    { header: 'Max (s)', key: 'max', width: 12 },
    { header: 'p50 (s)', key: 'p50', width: 12 },
    { header: 'p95 (s)', key: 'p95', width: 12 },
    { header: 'p99 (s)', key: 'p99', width: 12 }
  ];

  const dbData = [
    ['users', 'find', 'SUCCESS', 2094, 6.559, 0.003, 0.001, 0.116, 0.001, 0.012, 0.029],
    ['roles', 'find', 'SUCCESS', 1117, 3.304, 0.003, 0.001, 0.077, 0.001, 0.011, 0.027],
    ['executions', 'insert', 'SUCCESS', 556, 5.045, 0.009, 0.007, 0.079, 0.007, 0.024, 0.042],
    ['executions', 'update', 'SUCCESS', 556, 5.337, 0.010, 0.007, 0.134, 0.007, 0.025, 0.050],
    ['executions', 'find', 'SUCCESS', 556, 1.605, 0.003, 0.001, 0.112, 0.001, 0.010, 0.025],
    ['executions', 'createIndexes', 'SUCCESS', 4, 0.016, 0.004, 0.002, 0.010, 0.002, 0.010, 0.010],
    ['audit_logs', 'insert', 'SUCCESS', 476, 1.366, 0.003, 0.001, 0.078, 0.001, 0.011, 0.023],
    ['system', 'commitTransaction', 'SUCCESS', 476, 4.286, 0.009, 0.007, 0.129, 0.007, 0.022, 0.054],
    ['members', 'insert', 'SUCCESS', 296, 0.988, 0.003, 0.001, 0.050, 0.001, 0.016, 0.032],
    ['idempotency_keys', 'find', 'SUCCESS', 296, 0.890, 0.003, 0.001, 0.114, 0.001, 0.011, 0.021],
    ['idempotency_keys', 'insert', 'SUCCESS', 296, 0.729, 0.002, 0.001, 0.022, 0.001, 0.009, 0.019],
    ['idempotency_keys', 'createIndexes', 'SUCCESS', 2, 0.002, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001],
    ['members', 'find', 'SUCCESS', 162, 0.521, 0.003, 0.002, 0.051, 0.002, 0.012, 0.020],
    ['members', 'findAndModify', 'SUCCESS', 132, 0.443, 0.003, 0.002, 0.019, 0.002, 0.013, 0.019],
    ['users', 'update', 'SUCCESS', 26, 0.077, 0.003, 0.002, 0.016, 0.002, 0.016, 0.017],
    ['users', 'insert', 'SUCCESS', 24, 0.042, 0.002, 0.001, 0.006, 0.001, 0.005, 0.007],
    ['users', 'aggregate', 'SUCCESS', 24, 0.049, 0.002, 0.001, 0.007, 0.001, 0.005, 0.007],
    ['roles', 'aggregate', 'SUCCESS', 24, 0.064, 0.003, 0.002, 0.016, 0.002, 0.005, 0.016],
    ['roles', 'update', 'SUCCESS', 6, 0.013, 0.002, 0.002, 0.003, 0.002, 0.003, 0.003],
    ['system', 'getMore', 'SUCCESS', 24, 0.034, 0.001, 0.001, 0.005, 0.001, 0.003, 0.005],
    ['system', 'listDatabases', 'SUCCESS', 1, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001]
  ];

  dbData.forEach(row => dbSheet.addRow(row));
  styleTable(dbSheet, 1, 22, 1, 11);
  dbSheet.getRow(1).height = 25;

  await workbook.xlsx.writeFile(outputPath);
  console.log(`[Excel Export] Successfully generated report at: ${outputPath}`);
}

generateReport().catch(err => {
  console.error('[Excel Export] Error:', err);
  process.exit(1);
});
