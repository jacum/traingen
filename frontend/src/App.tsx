import './App.css'

import { paths } from './services/user-api.ts'
import createClient from "openapi-fetch";
import {
    QueryClient,
    QueryClientProvider,
    useQuery,
} from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'

const queryClient = new QueryClient()

export default function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <ReactQueryDevtools />
            <Combo />
        </QueryClientProvider>
    )
}

const client = createClient<paths>({ baseUrl:  "" });

function Combo() {
    const {isPending, error, data, isFetching} = useQuery({
        queryKey: ['repoData'],
        queryFn: async () => await client.GET("/user/api/combo/make", {
            params: {},
        }),
    })

    if (isPending || isFetching) return 'Loading...'

    if (error) return 'An error has occurred: ' + error.message

    return (<table>
        <thead>
        <tr>
            <th>Combo</th>
        </tr>
        </thead>
        <tbody key="movements">
        {data.data?.movements.map( (m, i) =>
                <tr key={i}>
                    <td key={i}>{m.description}</td>
                </tr>
        )}
    </tbody>
</table>)
}

