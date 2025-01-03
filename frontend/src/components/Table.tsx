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