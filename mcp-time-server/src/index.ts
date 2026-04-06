import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { getCurrentTimeISO } from './time.js';

const server = new McpServer({
  name: 'time',
  version: '1.0.0',
});

server.tool(
  'get_current_time',
  'Returns the current local time in ISO 8601 format with UTC offset',
  {},
  async () => ({
    content: [{ type: 'text', text: getCurrentTimeISO() }],
  })
);

const transport = new StdioServerTransport();
await server.connect(transport);
