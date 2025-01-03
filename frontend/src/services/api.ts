import { makeFetch } from "openapi-typescript-fetch";
import axios from "axios";
import openApiSpec from "./openapi-spec.json";

const fetcher = makeFetch({ baseUrl: "http://localhost:8080" });
fetcher.configure({
  fetch: (url, options) => axios(url, options).then(res => res.data),
});

export default fetcher;

// 4. Create src/components/Table.tsx
import React from "react";

type TableProps = {
  data: { [key: string]: any }[];
};

const Table: React.FC<TableProps> = ({ data }) => {
  return (
    <div className="overflow-x-auto">
      <table className="table-auto w-full border-collapse border border-gray-300">
        <thead>
          <tr>
            {Object.keys(data[0] || {}).map((key) => (
              <th
                key={key}
                className="border border-gray-300 px-4 py-2 bg-gray-100 text-left"
              >
                {key}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, idx) => (
            <tr key={idx}>
              {Object.values(row).map((value, cellIdx) => (
                <td
                  key={cellIdx}
                  className="border border-gray-300 px-4 py-2"
                >
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default Table;