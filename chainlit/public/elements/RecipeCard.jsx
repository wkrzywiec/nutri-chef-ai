import { Card } from "@/components/ui/card"
import { ExternalLink } from "lucide-react"

export default function RecipeCard() {
  return (
    <Card className="w-full overflow-hidden flex flex-row">
      {props.imageUrl && (
        <div className="w-[300px] h-[300px] shrink-0 overflow-hidden">
          <img
            src={props.imageUrl}
            alt={props.name}
            className="h-full w-full object-cover"
          />
        </div>
      )}
      <div className="flex flex-col justify-start p-3 gap-1 flex-1 min-w-0">
        <span className="text-base font-semibold leading-snug">
          {props.name || "Unknown recipe"}
        </span>
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
      </div>
    </Card>
  )
}
