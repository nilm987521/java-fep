import * as assert from 'assert';
import * as vscode from 'vscode';

suite('Extension Test Suite', () => {
  vscode.window.showInformationMessage('Start all tests.');

  test('Extension should be present', () => {
    assert.ok(vscode.extensions.getExtension('fep.fep-bpmn-editor'));
  });

  test('Extension should activate', async () => {
    const extension = vscode.extensions.getExtension('fep.fep-bpmn-editor');
    if (extension) {
      await extension.activate();
      assert.strictEqual(extension.isActive, true);
    }
  });

  test('Commands should be registered', async () => {
    const commands = await vscode.commands.getCommands(true);

    const expectedCommands = [
      'fep-bpmn.openEditor',
      'fep-bpmn.scanDelegates',
      'fep-bpmn.newBpmnFile',
      'fep-bpmn.exportSvg',
      'fep-bpmn.exportPng'
    ];

    expectedCommands.forEach(cmd => {
      assert.ok(commands.includes(cmd), `Command ${cmd} should be registered`);
    });
  });
});
