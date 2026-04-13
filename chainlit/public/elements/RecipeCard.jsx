import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ExternalLink } from "lucide-react"

export default function RecipeCard() {
  return (
    <Card className="w-72 shrink-0 overflow-hidden">
      {props.imageUrl && (
        <div className="h-40 w-full overflow-hidden">
          <img
            src={props.imageUrl}
            alt={props.name}
            className="h-full w-full object-cover"
          />
        </div>
      )}
      <CardHeader className="pb-1 pt-3">
        <CardTitle className="text-sm font-semibold leading-snug">
          {props.name || "Unknown recipe"}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 pb-3">
        {props.description && (
          <p className="text-xs text-muted-foreground line-clamp-3">
            {props.description}
          </p>
        )}
        {props.sourceUrl && (
          <a
            href={props.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
          >
            <ExternalLink className="h-3 w-3" />
            {props.source || "Source"}
          </a>
        )}
      </CardContent>
    </Card>
  )
}
